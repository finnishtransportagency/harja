(ns harja.palvelin.integraatiot.jms
  (:require [clojure.core.async :as async]
            [clojure.spec.alpha :as s]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [clojure.string :as clj-str]
            [cheshire.core :as cheshire]
            [slingshot.slingshot :refer [try+ throw+]]
            [taoensso.timbre :as log]
            [com.stuartsierra.component :as component]
            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.palvelin.tyokalut.komponentti-protokollat :as kp]
            [harja.palvelin.tyokalut.tapahtuma-apurit :as tapahtuma-apurit]
            [harja.kyselyt.jarjestelman-tila :as q]
            [harja.tyokalut.predikaatti :as predikaatti])
  (:import (clojure.lang PersistentArrayMap)
           (javax.jms Session ExceptionListener JMSException MessageListener TextMessage)
           (java.util Date)
           (java.lang.reflect Proxy InvocationHandler)
           (java.net InetAddress)))

(s/def ::virhe any?)
(s/def ::yhteyden-tila any?)
(s/def ::istunnot any?)
(s/def ::olioiden-tilat (s/keys :req-un [::yhteyden-tila ::istunnot]))
(s/def ::jms-epaonnistunut-viesti (s/keys :req-un [::virhe]))
(s/def ::jms-onnistunut-viesti ::olioiden-tilat)
(s/def ::jms-viesti (fn [m]
                      (let [[jarjestelma viesti] (first m)]
                        (and (map? m)
                             (= (count m) 1)
                             (string? jarjestelma)
                             (or (s/valid? ::jms-onnistunut-viesti viesti)
                                 (s/valid? ::jms-epaonnistunut-viesti viesti))))))
(s/def ::jms-tila ::jms-viesti)

(def aktiivinen "ACTIVE")
(def sammutettu "CLOSED")

(defonce jms-saije-sammutettu? (atom {}))

(def JMS-alkutila
  {:yhteys nil :istunnot {}})

(def ei-jms-yhteytta {:type :jms-yhteysvirhe
                      :virheet [{:koodi :ei-yhteytta
                                 :viesti "Sonja yhteyttä ei saatu. Viestiä ei voida lähettää."}]})

(def aikakatkaisu-virhe {:type :jms-kaskytysvirhe
                         :virheet [{:koodi :ruuhkaa
                                    :viesti "Sonja-säije ei kyennyt käsittelemään viestiä ajallaan."}]})
(def kasykytyskanava-taynna-virhe {:type :jms-kaskytysvirhe
                                   :virheet [{:koodi :taynna
                                              :viesti "Sonja-säije ei pysty käsittelemään enempää viestejä."}]})
(def jms-saije-sammutetaan-virhe {:type :jms-kaskytysvirhe
                                  :virheet [{:koodi :saije-sammutetaan
                                             :viesti "Sonja-säijetta samutetaan."}]})
(def jms-saije-sammutettu-virhe {:type :jms-kaskytysvirhe
                                 :virheet [{:koodi :saije-sammutettu
                                            :viesti "Sonja-säije on sammutettu."}]})

(def viestin-kasittely-timeout 15000)

(def jms-driver-luokka {:activemq "org.apache.activemq.ActiveMQConnectionFactory"
                        :sonicmq "progress.message.jclient.QueueConnectionFactory"})

(defprotocol LuoViesti
  (luo-viesti [x istunto]))

(extend-protocol LuoViesti
  String
  (luo-viesti [s istunto]
    (doto (.createTextMessage istunto)
      (.setText s)))
  ;; Luodaan multipart viesti
  PersistentArrayMap
  (luo-viesti [{:keys [xml-viesti pdf-liite]} istunto]
    (if (and xml-viesti pdf-liite)
      (let [mm (.createMultipartMessage istunto)
            viesti-osio (.createMessagePart mm (luo-viesti xml-viesti istunto))
            liite-osio (.createMessagePart mm (doto (.createBytesMessage istunto)
                                                (.writeBytes pdf-liite)))]
        (doto mm
          (.addPart viesti-osio)
          (.addPart liite-osio)))
      (throw+ {:type :puutteelliset-multipart-parametrit
               :virheet [(if xml-viesti
                           "XML viesti annettu"
                           {:koodi :ei-xml-viestia
                            :viesti "XML-viestiä ei annettu"})
                         (if pdf-liite
                           "PDF liite annettu"
                           {:koodi :ei-pdf-liitetta
                            :viest "PDF-liitettä ei annettu"})]}))))

(defprotocol JMS
  (kuuntele! [this jonon-nimi kuuntelija-fn] [this jonon-nimi kuuntelija-fn jarjestelma]
    "Lisää uuden kuuntelijan annetulle jonolle. Jos jonolla on monta kuuntelijaa,
    viestit välitetään jokaiselle kuuntelijalle.
    Kuuntelijafunktiolle annetaan suoraan javax.jms.Message objekti.
    Kuuntelija blokkaa käsittelyn ajan, joten samasta jonosta voidaan lukea vain yksi viesti kerrallaan.
    Kuuntelija ei saa luoda uutta säijettä, koska AUTO_ACKNOWLEDGE on päällä. Tämä tarkoittaa sitä, että jos viestin
    käsittely epäonnistuu uudessa säikeessä ja varsinainen consumer on lopettanut jo hommansa, niin viesti on jo poistettu
    jonosta.
    Jos 'jarjestelma' on annettu, niin tässä määritetyn jonon viestit käsitellään samassa sessiossa kuin muut
    kuuntelijat ja lähettäjät samalla 'jarjestelma' nimellä.")

  (laheta [this jono viesti] [this jono viesti otsikot] [this jono viesti otsikot jarjestelma]
    "Lähettää viestin nimettyyn jonoon. Palauttaa message id:n.
    Jos 'jarjestelma' on annettu, niin tässä määritetyn jonon viestit käsitellään samassa sessiossa kuin muut
    kuuntelijat ja lähettäjät samalla 'jarjestelma' nimellä.")

  (sammuta-lahettaja [this jonon-nimi] [this jonon-nimi jarjestelma])

  (kasky [this kasky]
    "Lähettää käskyn sonja komponentille"))

(defmacro exception-wrapper [olio metodi]
  `(try (. ~olio ~metodi)
        aktiivinen
        ~(list 'catch 'javax.jms.IllegalStateException 'e
               sammutettu)
        ~(list 'catch 'Throwable 't
               nil)))

;; SonicMQ API:n avulla ei voi tarkistella suoraan onko sessio, vastaanottaja tai tuottaja sulettu,
;; joten täytyy yrittää käyttää sitä objektia johonkin ja katsoa nakataanko IllegalStateException
(defn istunnon-tila
  [istunto]
  (exception-wrapper istunto getAcknowledgeMode))

(defn tuottajan-tila [tuottaja]
  (exception-wrapper tuottaja getDeliveryMode))

(defn vastaanottajan-tila [vastaanottaja]
  (exception-wrapper vastaanottaja getMessageListener))

(defn aloita-jms [jarjestelma]
  (async/go
    (let [nimi (:nimi jarjestelma)]
      (log/info (str "Aloitaetaan " nimi))
      (loop []
        (let [{:keys [vastaus virhe kaskytysvirhe]} (async/<! (kasky jarjestelma {:aloita-yhteys nil}))]
          (when vastaus
            (log/info (str nimi " yhteys aloitettu")))
          (when kaskytysvirhe
            (log/error (str "Yhteyden aloittamisessa järjestelmään " nimi " käskytysvirhe: " kaskytysvirhe)))
          (async/<! (async/timeout 2000))
          (if (or virhe (= :kasykytyskanava-taynna kaskytysvirhe))
            (recur)
            vastaus))))))

(defn olion-tila-aktiivinen? [tila]
  (= tila aktiivinen))

(defn jono-ok? [jonon-tiedot]
  (let [{:keys [tuottaja vastaanottaja]} (first (vals jonon-tiedot))
        tuottajan-tila-ok? (when tuottaja
                             (olion-tila-aktiivinen? (:tuottajan-tila tuottaja)))
        vastaanottajan-tila-ok? (when vastaanottaja
                                  (olion-tila-aktiivinen? (:vastaanottajan-tila vastaanottaja)))]
    (every? #(not (false? %))
            [tuottajan-tila-ok? vastaanottajan-tila-ok?])))

(defn istunto-ok? [{:keys [jonot istunnon-tila]}]
  (and (olion-tila-aktiivinen? istunnon-tila)
       (not (empty? jonot))
       (every? jono-ok?
               jonot)))

(defn jmsyhteys-ok?
  [{:keys [istunnot yhteyden-tila]}]
  (boolean
    (and (olion-tila-aktiivinen? yhteyden-tila)
         (not (empty? istunnot))
         (every? istunto-ok?
                 istunnot))))

(defn hae-jonon-viestit
  "Luodaan selailija olio aina uudestaan, koska se saa jonosta snapshot tilanteen silloin, kun se luodaan."
  [istunto jono]
  (when (and istunto jono)
    ;; ainakin ActiveMQ:ssa createBrowser saattaa blokata ikuisesti jos istunto ei ole kunnossa. Tämän takia luodaan
    ;; browseri omassa säikessään vaikkei näin suositella tekevän. Jms-säije kumminkin työstää tämän läpi ennen
    ;; kuin tekee mitään muuta
    (first (async/alts!! [(async/thread (try (with-open [selailija (.createBrowser istunto jono)]
                                               (let [viesti-elementit (.getEnumeration selailija)]
                                                 (loop [elementit []]
                                                   ;; Selailija olio ei ainakaan ActiveMQ:n kanssa toimi oikein kun istunnossa on useita eri jonoja. hasMoreElements
                                                   ;; palauttaa oikein true sillin, kun siellä on viestejä, mutta nextElement palauttaa siitä huolimatta nil
                                                   (if (.hasMoreElements viesti-elementit)
                                                     (let [elementti (.nextElement viesti-elementit)]
                                                       (if (nil? elementti)
                                                         (conj elementit nil)
                                                         (recur (conj elementit elementti))))
                                                     elementit))))
                                             (catch Throwable t
                                               nil)))
                          (async/timeout 500)]))))

(declare tee-jms-poikkeuskuuntelija
         laheta-viesti-kaskytyskanavaan!
         jms-toiminto!)

(defn- tallenna-jms-tila-kantaan
  [db jms-tila jarjestelma]
  {:pre [(seqable? jms-tila)
         (string? jarjestelma)]}
  (q/tallenna-jarjestelman-tila<! db {:tila (cheshire/encode jms-tila)
                                      :palvelin (fmt/leikkaa-merkkijono 512
                                                                        (.toString (InetAddress/getLocalHost)))
                                      :osa-alue jarjestelma}))

(defn aloita-jms-yhteyden-tarkkailu [this paivitystiheys-ms lopeta-tarkkailu-kanava tapahtuma-julkaisija db]
  (tapahtuma-apurit/loop-f lopeta-tarkkailu-kanava
                           paivitystiheys-ms
                           (fn []
                             (try
                               (let [jms-tila (::kp/tiedot (kp/status this))]
                                 (tallenna-jms-tila-kantaan db (get jms-tila (:nimi this)) (:nimi this))
                                 (tapahtuma-julkaisija jms-tila))
                               (catch Throwable t
                                 (tapahtuma-julkaisija {(:nimi this) {:virhe :tilan-lukemisvirhe}})
                                 (log/error (str "Jms tilan lukemisessa virhe jarjestelmässä "
                                                 (:nimi this) ": "
                                                 (.getMessage t)))
                                 (binding [*out* *err*]
                                   (log/error "Stack trace:"))
                                 (.printStackTrace t))))))

(defn tee-sonic-jms-tilamuutoskuuntelija [jms-connection-tila]
  (let [lokita-tila #(case %
                       0 (do (log/info "Sonja JMS yhteyden tila: ACTIVE")
                             (reset! jms-connection-tila "ACTIVE"))
                       1 (do (log/info "Sonja JMS yhteyden tila: RECONNECTING")
                             (reset! jms-connection-tila "RECONNECTING"))
                       2 (do (log/error "Sonja JMS yhteyden tila: FAILED")
                             (reset! jms-connection-tila "FAILED"))
                       3 (do (log/info "Sonja JMS yhteyden tila: CLOSED")
                             (reset! jms-connection-tila "CLOSED")))
        kasittelija (reify InvocationHandler (invoke [_ _ _ args] (lokita-tila (first args))))
        luokka (Class/forName "progress.message.jclient.ConnectionStateChangeListener")]
    (Proxy/newProxyInstance (.getClassLoader luokka) (into-array Class [luokka]) kasittelija)))

(defn tee-activemq-jms-tilanmuutoskuuntelija [jms-connection-tila]
  (reify org.apache.activemq.transport.TransportListener
    (onCommand [this komento]
      ;; Ei tehdä tässä mitään, mutta kaikki interfacen metodit on implementoitava.
      )
    (onException [this e] (reset! jms-connection-tila "FAILED"))
    (transportInterupted [this] (reset! jms-connection-tila "RECONNECTING"))
    (transportResumed [this] (reset! jms-connection-tila "ACTIVE"))))

(defn tee-jms-poikkeuskuuntelija []
  (reify ExceptionListener
    (onException [_ e]
      (log/error e (str "Tapahtui JMS-poikkeus: " (.getMessage e))))))

(defn konfiguroi-activemq-jms-connection-factory [connection-factory url]
  (doto connection-factory
    (.setBrokerURL (str "failover:(" url ")?initialReconnectDelay=100"))))

(defmacro esta-class-not-found-virheet [tyyppi body]
  `(when (= ~tyyppi :sonicmq)
     (try
       (eval ~body)
       (catch Throwable ~'t
         (taoensso.timbre/error "JMS eval EPÄONNISTUI: " (.getMessage ~'t) "\nStackTrace: " (.getStackTrace ~'t))))))

(defn konfiguroi-sonic-jms-connection-factory [connection-factory tyyppi]
  (doto connection-factory
    ;; Fault tolerant –asetuksen pois kytkeminen puolestaan johtuu siitä, että jos se on käytössä, client vastaanottaa JMS-”palvelimelta” tiedon siitä,
    ;; mitä osoitteita ja portteja on käytössä samassa klusterissa – ja ainakin aiemmin Sonja oli konfiguroitu siten, että se antoi täsmälleen yhden osoitteen,
    ;; johon yhteyden katketessa pantiin sitten hanskat tiskiin.
    (.setFaultTolerant false)
    ;; Pingillä pyritään pitämään hengissä olemassa olevaa yhteyttä, vaikka siellä ei varsinaisesti liikettä olisikaan.
    (.setPingInterval (int 30))
    ;; Yrittää reconnectata loputtomiin. Pitää wrapata intiin, jotta tyypiksi tulee Integer, eikä Float
    (.setFaultTolerantReconnectTimeout (int 0))
    ;; Configuroidaan niin, että lähetykset tapahtuu asyncisti. Tämä on ok, sillä vastauksia jäädään muutenkin
    ;; odottamaan eri jonoon. Asyncisti sen takia, että JMS-säije ei blokkaa lähetykseen. Mahdolliset virheet
    ;; otetaan kiinni sitten yhteysolion setRefectionListener:issä
    (.setAsynchronousDeliveryMode (esta-class-not-found-virheet tyyppi '(. progress.message.jclient.Constants ASYNC_DELIVERY_MODE_ENABLED)))))

(defn- luo-connection-factory [url tyyppi]
  (let [connection-factory (-> tyyppi
                               jms-driver-luokka
                               Class/forName
                               (.getConstructor (into-array Class [String]))
                               (.newInstance (into-array Object [url])))]
    (if (= tyyppi :activemq)
      (konfiguroi-activemq-jms-connection-factory connection-factory url)
      (konfiguroi-sonic-jms-connection-factory connection-factory tyyppi))))

(defn luo-istunto [yhteys]
  (try
    (.createQueueSession yhteys false Session/AUTO_ACKNOWLEDGE)
    (catch JMSException e
      (log/error "Sonja ei saanut luotua sessiota. " (.getMessage e) "\n stackTrace: " (.getStackTrace e)))))

(defn- yhdista [{:keys [kayttaja salasana tyyppi] :as asetukset} qcf aika jms-connection-tila]
  (try
    (let [yhteys (.createQueueConnection qcf kayttaja salasana)]
      (if (= tyyppi :sonicmq)
        (doto yhteys
          (.setConnectionStateChangeListener (tee-sonic-jms-tilamuutoskuuntelija jms-connection-tila))
          ;; Otetaan kiinni epäonnistuneet lähetykset
          (.setRejectionListener (esta-class-not-found-virheet tyyppi
                                                               '(reify progress.message.jclient.RejectionListener
                                                                  (onRejectedMessage [this msg e]
                                                                    (try
                                                                      (taoensso.timbre/error
                                                                        (str "Harjasta on lähetetty viesti Sonjan kautta jonnekkin, mutta"
                                                                             " sitä viestiä ei saatu vastaanottopäässä käsiteltyä."))
                                                                      (taoensso.timbre/error
                                                                        (str "Sonjalta tullut virhe msg: " msg
                                                                             " Virhekoodi: " (.getErrorCode e)))
                                                                      ;; Halutaan ottaa kaikki virheet kiinni, sillä yksikin käsittelemätön virhe
                                                                      ;; eaiheuttaa muiden viestien käsittelyn.

                                                                      ;; Älä tee mitään aikaa vievää täällä. Muuten yhteyttä tai sessiota ei saada välttämättä kiinni.
                                                                      (catch Throwable t
                                                                        (taoensso.timbre/error (str "Epäonnistuneen viestin käsittely epäonnistui: "
                                                                                                    (.getMessage t) "\nStackTrace: "))
                                                                        (.printStackTrace t))))))))
        (.addTransportListener yhteys (tee-activemq-jms-tilanmuutoskuuntelija jms-connection-tila)))
      yhteys)
    (catch JMSException e
      (log/warn (format "Ei saatu yhteyttä JMS-brokeriin. Yritetään uudestaan %s millisekunnin päästä. " aika) e)
      (Thread/sleep aika)
      (yhdista asetukset qcf (min (* 2 aika) 600000) jms-connection-tila))
    (catch Throwable t
      (log/error "JMS brokeriin yhdistäminen epäonnistui: " (.getMessage t) "\nStackTrace: ")
      (.printStackTrace t)
      (Thread/sleep aika)
      (yhdista asetukset qcf (min (* 2 aika) 600000) jms-connection-tila))))

(defn aloita-yhdistaminen [{:keys [url tyyppi] :as asetukset} jms-connection-tila]
  (log/info "Yhdistetään " (if (= tyyppi :activemq) "ActiveMQ" "Sonic") " JMS-brokeriin URL:lla:" url)
  (let [qcf (luo-connection-factory url tyyppi)
        yhteys (yhdista asetukset qcf 10000 jms-connection-tila)]
    ;; Jos yhteys olio ollaan saatu luotua, mutta se on kiinni tilassa (brokerin päässä saattaa olla jotain
    ;; vikaa), niin yritetään luoda yhteyttä vielä uudestaan.
    (if (case tyyppi
          :activemq (not (or (.isClosed yhteys) (.isClosing yhteys)))
          ; ACTIVE:n arvo on 0
          :sonicmq (= (.getConnectionState yhteys) 0))
      (do
        (log/info "Yhteyden metadata: " (when-let [meta-data (.getMetaData yhteys)]
                                          (.getJMSProviderName meta-data)))
        (log/info "Saatiin yhteys Sonjan JMS-brokeriin.")
        {:yhteys yhteys :qcf qcf})
      (do
        (log/error "Jokin meni vikaan, kun yritettiin saada yhteys Sonjaan... Yritetään yhdistää uudelleen.")
        ;; Yhteys objekti kummiskin pitää sammuttaa, jotta ylimääräiset säikeet sammutetaan ja muistia vapautetaan
        (try (.close yhteys)
             (catch Throwable t
               (log/debug "EI saatu sulettua epäonnistunutta yhteyttä")))
        (async/<!! (async/timeout 10000))
        (aloita-yhdistaminen asetukset jms-connection-tila)))))

(defn aseta-viestien-kasittelija! [vastaanottaja kuuntelijat tila jarjestelma jonon-nimi]
  (.setMessageListener vastaanottaja
                       (with-meta
                         (reify MessageListener
                           (onMessage [_ message]
                             (log/debug "Saatiin viesti jonoon: " jonon-nimi)
                             (doseq [kuuntelija kuuntelijat]
                               (try
                                 (kuuntelija message)
                                 (catch Throwable t
                                   (log/error (str "Jonoon " (-> vastaanottaja .getQueue .getQueueName) " tuli viesti "
                                                   (if (instance? TextMessage message)
                                                     (.getText message)
                                                     message)
                                                   " ja sen käsittely epäonnistui funktiolta " kuuntelija
                                                   " virheeseen " (.getMessage t)
                                                   "\nStackTrace: "))
                                   (.printStackTrace t)
                                   (swap! tila update-in [:istunnot jarjestelma :jonot jonon-nimi :virheet]
                                          (fn [virheet]
                                            (into []
                                                  ;; Otetaan maksimissaan 10 virhettä, jotta sylttyy tilanteessa
                                                  ;; ei muisti ala täyttymään virheviesteistä
                                                  (take-last 10
                                                             (conj (or virheet [])
                                                                   {:viesti (.getMessage t)
                                                                    :aika (.toString (pvm/nyt-suomessa))}))))))))))
                         {:kuuntelijoiden-maara (count kuuntelijat)})))

(defn varmista-jms-objektit! [tila jonon-nimi jarjestelma kasittelija]
  (let [{istunnot :istunnot yhteys :yhteys} @tila
        {istunto :istunto jonot :jonot} (get istunnot jarjestelma)
        {jono :jono kasittelija-olio kasittelija kuuntelijat :kuuntelijat} (get jonot jonon-nimi)]
    (when-not (and istunto jono kasittelija-olio)
      (let [istunnon-tila (when istunto
                            (istunnon-tila istunto))
            vanha-istunto (when (= aktiivinen istunnon-tila) istunto)
            istunto (or vanha-istunto (luo-istunto yhteys))
            jono (or (and vanha-istunto jono) (.createQueue istunto jonon-nimi))
            kasittelija-olio (or (and vanha-istunto kasittelija-olio)
                                 (if (= kasittelija :vastaanottaja)
                                   (let [vastaanottaja (.createReceiver istunto jono)]
                                     (aseta-viestien-kasittelija! vastaanottaja kuuntelijat tila jarjestelma jonon-nimi)
                                     vastaanottaja)
                                   (.createProducer istunto jono)))]
        (swap! tila (fn [tiedot]
                      (-> tiedot
                          (assoc-in [:istunnot jarjestelma :istunto] istunto)
                          (assoc-in [:istunnot jarjestelma :jonot jonon-nimi :jono] jono)
                          (assoc-in [:istunnot jarjestelma :jonot jonon-nimi kasittelija] kasittelija-olio))))))))

(defn laheta-viesti [tila jonon-nimi viesti correlation-id jarjestelma]
  (try
    (let [{istunnot :istunnot} @tila
          {istunto :istunto jonot :jonot} (get istunnot jarjestelma)
          {tuottaja :tuottaja} (get jonot jonon-nimi)
          msg (luo-viesti viesti istunto)]
      (log/debug "Lähetetään JMS viesti ID:llä " (.getJMSMessageID msg))
      (when correlation-id
        (.setJMSCorrelationID msg correlation-id))
      (.send tuottaja msg)
      (.getJMSMessageID msg))
    (catch Exception e
      (swap! tila update-in [:istunnot jarjestelma :jonot jonon-nimi :virheet]
             (fn [virheet]
               (into []
                     ;; Otetaan maksimissaan 10 virhettä, jotta sylttyy tilanteessa
                     ;; ei muisti ala täyttymään virheviesteistä
                     (take-last 10
                                (conj (or virheet [])
                                      {:viesti (.getMessage e)
                                       :aika (.toString (pvm/nyt-suomessa))})))))
      (log/error e "Virhe JMS-viestin lähettämisessä jonoon: " jonon-nimi))))

(defn yhteys-oliot!
  [yhteys-future]
  (let [[yhteys-oliot _] (async/alts!! [(async/timeout 5000) (async/thread @yhteys-future)])]
    (if yhteys-oliot
      yhteys-oliot
      (do (log/info "Ei saatu yheys olioita ajallaan")
          (yhteys-oliot! yhteys-future)))))

(defn jms-client-tila [jms-tila jms-connection-tila]
  {:yhteyden-tila @jms-connection-tila
   :istunnot (mapv (fn [[jarjestelma istunto-tiedot]]
                     (let [{:keys [jonot istunto]} istunto-tiedot
                           istunnon-tila (istunnon-tila istunto)]
                       {:istunnon-tila istunnon-tila
                        :jarjestelma jarjestelma
                        :jonot (mapv (fn [[jonon-nimi {:keys [tuottaja vastaanottaja virheet]}]]
                                       {jonon-nimi (cond-> {}
                                                           tuottaja
                                                           (merge {:tuottaja {:tuottajan-tila (tuottajan-tila tuottaja)
                                                                              :virheet virheet}})
                                                           vastaanottaja
                                                           (merge {:vastaanottaja {:vastaanottajan-tila (vastaanottajan-tila vastaanottaja)
                                                                                   :virheet virheet}}))})
                                     jonot)}))
                   (:istunnot jms-tila))})

(defmulti jms-toiminto!
          (fn [_ kasky]
            (-> kasky keys first)))

(defmethod jms-toiminto! :aloita-yhteys
  [{:keys [tila yhteys-aloitettu? jms-connection-tila nimi]} _]
  (loop [yhteys-oliot-luotu? (not= JMS-alkutila @tila)]
    (when-not yhteys-oliot-luotu?
      (async/<!! (async/timeout 1000))
      (recur (not= JMS-alkutila @tila))))
  (try (let [{:keys [istunnot yhteys]} @tila
             poikkeuskuuntelija (tee-jms-poikkeuskuuntelija)]
         ;; Alustetaan vastaanottaja jms oliot
         (doseq [[jarjestelma {jonot :jonot}] istunnot]
           (doseq [[jonon-nimi _] jonot]
             (varmista-jms-objektit! tila jonon-nimi jarjestelma :vastaanottaja)))
         ;; Lisätään poikkeuskuuntelija yhteysolioon
         (.setExceptionListener yhteys poikkeuskuuntelija)
         ;; Aloita yhteys
         (log/debug "Aloitetaan yhteys")
         (.start yhteys)
         (reset! jms-connection-tila "ACTIVE")
         (reset! yhteys-aloitettu? true)
         (tapahtuma-apurit/julkaise-tapahtuma :jms-yhteys-aloitettu {:jarjestelma nimi})
         true)
       (catch Exception e
         (log/error "VIRHE TAPAHTUI :aloita-yhteys " (.getMessage e) "\nStackTrace: ")
         (.printStackTrace e)
         {:virhe e})))

(defmethod jms-toiminto! :lopeta-yhteys
  [{:keys [tila yhteys-aloitettu? jms-connection-tila]} _]
  (try (let [{:keys [yhteys]} @tila]
         (log/debug "Lopetetaan yhteys")
         (.close yhteys)
         (reset! jms-connection-tila "CLOSED")
         (reset! yhteys-aloitettu? false)
         true)
       (catch Exception e
         (log/error "VIRHE TAPAHTUI :lopeta-yhteys " (.getMessage e) "\nStackTrace: ")
         (.printStackTrace e)
         {:virhe e})))


(defmethod jms-toiminto! :laheta-viesti
  [{:keys [tila]} kasky]
  (try (let [params (when-let [params (vals kasky)]
                      (first params))
             [jonon-nimi viesti {:keys [correlation-id]} jarjestelma] params]
         (varmista-jms-objektit! tila jonon-nimi jarjestelma :tuottaja)
         ;; Meidän ei tarvitse olla varmoja, että yhteys on aloitettu (eli start metodi on kutsuttu) silloin kun
         ;; lähetetään viestejä. JMS jonoissa se ei ole pakollista.
         (laheta-viesti tila jonon-nimi viesti correlation-id jarjestelma))
       (catch Exception e
         (log/error "VIRHE TAPAHTUI :laheta-viesti " (.getMessage e) "\nStackTrace: ")
         (.printStackTrace e)
         {:virhe e})))

(defmethod jms-toiminto! :poista-kuuntelija
  [{:keys [tila]} kasky]
  (try (let [params (when-let [params (vals kasky)]
                      (first params))
             [jarjestelma jonon-nimi kuuntelija-fn] params
             {:keys [jonot istunto]} (get-in @tila [:istunnot jarjestelma])
             {:keys [kuuntelijat vastaanottaja jono] :as jonon-tiedot} (get jonot jonon-nimi)
             kuuntelijat (disj kuuntelijat kuuntelija-fn)]
         ;; Jos viestiä käsitellään, kun vaihdetaan messageListeneriä, niin sen seuraukset ovat määrittelemättömät.
         ;; Sen takia ensin sammutetaan nykyinen kuuntelija, koska se ensin käsittelee käsitteillä olevat viestit ja blokkaa siksi aikaa.
         ;; Tämän jälkeen luodaan uusi vastaanottaja.
         (when vastaanottaja
           (.close vastaanottaja))
         (if (empty? kuuntelijat)
           (swap! tila assoc-in [:istunnot jarjestelma :jonot jonon-nimi] nil)
           (let [vastaanottaja (.createReceiver istunto jono)]
             (aseta-viestien-kasittelija! vastaanottaja kuuntelijat tila jarjestelma jonon-nimi)
             (swap! tila update-in [:istunnot jarjestelma :jonot jonon-nimi]
                    (fn [jonon-tiedot]
                      (assoc jonon-tiedot :vastaanottaja vastaanottaja
                                          :kuuntelijat kuuntelijat)))))
         true)
       (catch Exception e
         (log/error "VIRHE TAPAHTUI :poista-kuuntelija " (.getMessage e))
         (binding [*out* *err*]
           (log/error "Stack Trace:"))
         (.printStackTrace e)
         {:virhe e})))

(defmethod jms-toiminto! :poista-lahettaja
  [{:keys [tila]} kasky]
  (try (let [params (when-let [params (vals kasky)]
                      (first params))
             [jarjestelma jonon-nimi] params
             {:keys [jonot istunto]} (get-in @tila [:istunnot jarjestelma])
             {:keys [tuottaja jono] :as jonon-tiedot} (get jonot jonon-nimi)]
         (when tuottaja
           (.close tuottaja))
         (when (and jarjestelma jonon-nimi)
           (swap! tila assoc-in [:istunnot jarjestelma :jonot jonon-nimi] nil))
         true)
       (catch Exception e
         (log/error "VIRHE TAPAHTUI :poista-lahettaja " (.getMessage e))
         (binding [*out* *err*]
           (log/error "Stack Trace:"))
         (.printStackTrace e)
         {:virhe e})))

(defmethod jms-toiminto! :jms-tilanne
  [{:keys [tila jms-connection-tila] :as jms-client} _]
  (try (::kp/tiedot (kp/status jms-client))
       (catch Exception e
         (log/error "VIRHE TAPAHTUI :jms-tilanne " (.getMessage e) "\nStackTrace: ")
         (.printStackTrace e)
         {:virhe e})))

(defn kasittele-kasky!
  [{:keys [kasky kaskyn-kasittely-jms-saikeelle
           kaskyn-kasittely-kaskytys-saikeelle]}
   jms-client]
  (let [;; Ilmoitetaan, että voidaan käsitellä käsky. Kumminkin on huomioitava, että kasittelykanavaan on saatettu jo lähettää aikakatkaisuviesti
        [viesti kanava] (async/alts!! [[kaskyn-kasittely-kaskytys-saikeelle :valmis-kasiteltavaksi] kaskyn-kasittely-jms-saikeelle])]
    (cond
      (= kanava kaskyn-kasittely-jms-saikeelle) (when (= viesti :aikakatkaisu)
                                                  nil)
      (= kanava kaskyn-kasittely-kaskytys-saikeelle) (case (async/<!! kaskyn-kasittely-jms-saikeelle)
                                                       :kasittele (let [kasittelyn-alku (System/currentTimeMillis)
                                                                        vastaus (try (let [vastaus (jms-toiminto! jms-client kasky)]
                                                                                       (if (and (map? vastaus)
                                                                                                (contains? vastaus :virhe)
                                                                                                (= (count vastaus) 1))
                                                                                         vastaus
                                                                                         {:vastaus vastaus}))
                                                                                     (catch Throwable t
                                                                                       (log/error "Jokin meni vikaan sonjakäskyissä: " (.getMessage t) "\nStackTrace: ")
                                                                                       (.printStackTrace t)
                                                                                       {:virhe t}))
                                                                        kasittelyn-kesto (float (/ (- (System/currentTimeMillis) kasittelyn-alku)
                                                                                                   1000))]
                                                                    (when (> kasittelyn-kesto 1.5)
                                                                      (log/info "jms-säikeellä meni " kasittelyn-kesto " sekunttia käsitellä käsky: " kasky))
                                                                    (async/>!! kaskyn-kasittely-kaskytys-saikeelle vastaus))))))

(defn luo-jms-saije
  "JMS spesifikaation mukaan connection oliota voi käyttää ihan vapaasti useasta eri säikeestä, mutta session olioita ja
   kaikkia sen luomia olioita ei voi. Niitä tulisi kohdella säijekohtaisesti. Mikään ei estä niiden käsittelyä useasta eri
   säikeestä, mutta se on käyttäjän vastuulla, että samaa oliota ei käytetä yhtä aikaa, jonka takia tosiaan suositellaan,
   että manipulaatio tehdään yhdestä säikeestä. Tässä luodaan vain yksi säije, jossa luodaan kaikki sessiot ja siihen
   liittyvät muut oliot. Myöhemmin voi luoda useampia säikeitä joihin tulee vaikkapa vain tietyn järjestelmän sessiot, jos
   tähän on tarvesta. ActiveMQ ja SonicMQ ovat kumminkin implementoitu niin, että useamman session luominen johtaa jo
   threadpoolien käyttämiseen eikä kaikkea varsinaisesti suoriteta yhdessä säikeessä.

   Näitä jms-olioita käsitellään omassa säikeessään 'pääsäikeen' sijasta, jotta Harja järjestelmä voidaan muuten käynnistää
   vaikka olisi jotain ongelmia JMS clienttien kanssa."
  [{:keys [yhteys-future tila kaskytyskanava nimi] :as jms-client} sammutus-kanava]
  (async/thread
    (swap! jms-saije-sammutettu? assoc nimi false)
    ;; Ensin varmistetaan, että yhteys jms-clientan on saatu. futuren dereffaaminen blokkaa, kunnes saadaan
    ;; joku arvo pihalle.
    (let [yhteys-oliot (yhteys-oliot! yhteys-future)]
      (log/debug "Yhteys oliot valmiit")
      (swap! tila merge yhteys-oliot)
      ;; Aloitetaan ikuinen looppi
      (loop []
        (async/alt!!
          kaskytyskanava ([kasky _]
                          (log/debug (str "[JMS] - " nimi " Saatiin käsky: " kasky))
                          (if (nil? kasky)
                            (do (log/info "Yritettiin antaa sammutetulle käskytyskanavalle käsky")
                                (async/<!! (async/timeout 2000))
                                (recur))
                            (do (kasittele-kasky! kasky jms-client)
                                (recur))))
          sammutus-kanava (do
                            ;; Sammutetaan käskytyskanava, jottei sinne voi tulla enää lisää käskyjä
                            (async/close! kaskytyskanava)
                            ;; Lopetuksen jälkeen käsitellään ensin kaikki kanavassa jo olevat
                            ;; käskyt
                            (loop [kasky (async/poll! kaskytyskanava)]
                              (if (nil? kasky)
                                (do
                                  (jms-toiminto! jms-client {:lopeta-yhteys nil})
                                  (swap! jms-saije-sammutettu? assoc nimi false))
                                (do
                                  (kasittele-kasky! kasky jms-client)
                                  (recur (async/poll! kaskytyskanava)))))))))))

(defn laheta-viesti-kaskytyskanavaan!
  "Lähettää käskyn jms-saikeelle. Jos käskytyskanava on täynnä, palauttaa virheen.
   Lisäksi käskyä ei käsitellä, jos timeout kerkeää mennä loppuun.
   Palauttaa kanavan, josta tuloksen voi lukea. Tulos voi olla käskyn käsitelty tulos tai sitten virhe-map, jossa ilmoitetaan
   epäonnisuiko käsittely timeoutin vai täyden bufferin takia taikka sammutetun jms-saikeen takia."
  [kaskytyskanava kasky nimi]
  (async/thread
    (let [kaskyn-kasittely-jms-saikeelle (async/chan)
          kaskyn-kasittely-kaskytys-saikeelle (async/chan)
          ;; offer! ei blokkaa. Tässä tulee käyttää offer!:ia put!:in sijasta, sillä put! lähettää viestin kanavaan sitten, kun
          ;; siellä on tilaa toisin kuin offer!. dropping-bufferin käyttö kaskytyskanavalle taasen aiheuttaisi sen, ettei tiedetä
          ;; onko kanava täynnä vai ei, kun sinne yritetään lisätä tavaraa.
          lahetettiinko-kasky? (async/offer! kaskytyskanava {:kasky kasky
                                                             :kaskyn-kasittely-jms-saikeelle kaskyn-kasittely-jms-saikeelle
                                                             :kaskyn-kasittely-kaskytys-saikeelle kaskyn-kasittely-kaskytys-saikeelle})]
      (case lahetettiinko-kasky?
        ;; Käskyn lähetys onnistui
        true (async/alt!!
               ;; Timeoutin lisääminen aiheuttaa
               ;; siinä mielessä harmia, että jms-saikeen täytyy kysyä tältä säikeeltä, onko timeout kerennyt jo tapahuta siinä vaiheessa,
               ;; kun jms-saie olisi valmis käsittelemään tämän viestin.
               (async/timeout (if (and (map? kasky)
                                       (contains? kasky :aloita-yhteys))
                                60000
                                viestin-kasittely-timeout)) (do (async/put! kaskyn-kasittely-jms-saikeelle
                                                                            :aikakatkaisu)
                                                                {:kaskytysvirhe :aikakatkaisu})
               kaskyn-kasittely-kaskytys-saikeelle ([tulos _]
                                                    (case tulos
                                                      :valmis-kasiteltavaksi (do
                                                                               (async/>!! kaskyn-kasittely-jms-saikeelle :kasittele)
                                                                               (async/<!! kaskyn-kasittely-kaskytys-saikeelle)))))
        ;; Käskytyskanava on sammutettu
        false (if (= :sammutettu (get @jms-saije-sammutettu? nimi))
                {:kaskytysvirhe :jms-saije-sammutettu}
                {:kaskytysvirhe :jms-saijetta-sammutetaan})
        ;; Käskytyskanava on täynnä
        nil {:kaskytysvirhe :kasykytyskanava-taynna}))))

(defn oletusjarjestelmanimi [jonon-nimi]
  (str "istunto-" jonon-nimi))

(defrecord JMSClient [nimi asetukset]
  component/Lifecycle
  (start [{:keys [db] :as this}]
    (let [JMS-oliot (atom JMS-alkutila)
          yhteys-aloitettu? (atom false)
          jms-connection-tila (atom nil)
          ;; HUOM! käskytyskanavaan ei tulisi laittaa viestejä muuten kuin laheta-viesti-kaskytyskanavaan!
          ;; funktion kautta.
          kaskytyskanava (async/chan 100)
          lopeta-tarkkailu-kanava (async/chan)
          saikeen-sammutus-kanava (async/chan)
          ;; Tämä futuressa sen takia, koska yhdistämisen aloittamien voi mahdollisesti loopata ikuisuuden eikä haluta
          ;; estää HARJA:n käynnistymistä sen takia.
          yhteys-future (future
                          (aloita-yhdistaminen asetukset jms-connection-tila))
          this (assoc this
                 :tila JMS-oliot
                 :jms-connection-tila jms-connection-tila
                 :yhteys-aloitettu? yhteys-aloitettu?
                 :yhteys-future yhteys-future
                 :kaskytyskanava kaskytyskanava
                 :lopeta-tarkkailu-kanava lopeta-tarkkailu-kanava
                 :saikeen-sammutus-kanava saikeen-sammutus-kanava)
          jms-saije (luo-jms-saije this saikeen-sammutus-kanava)]
      (swap! (:tila this) assoc :jms-saije jms-saije)
      (if (:julkaise-tila? asetukset)
        (let [tapahtuma-julkaisija (tapahtuma-apurit/tapahtuma-datan-spec (tapahtuma-apurit/tapahtuma-julkaisija :jms-tila) ::jms-tila)]
          (assoc this
            :yhteyden-tiedot (aloita-jms-yhteyden-tarkkailu this (:paivitystiheys-ms asetukset) lopeta-tarkkailu-kanava tapahtuma-julkaisija db)))
        this)))

  (stop [{:keys [lopeta-tarkkailu-kanava kaskytyskanava saikeen-sammutus-kanava tila] :as this}]
    (when (and (get-in this [:asetukset :julkaise-tila?])
               (predikaatti/chan? lopeta-tarkkailu-kanava)
               (not (predikaatti/chan-closed? lopeta-tarkkailu-kanava)))
      (tapahtuma-apurit/julkaise-tapahtuma :jms-tila {(:nimi this) :suljetaan})
      (async/>!! lopeta-tarkkailu-kanava true)
      (async/close! lopeta-tarkkailu-kanava))
    ;; Jos on jossain muuaalla jo käsketty sammuuttaa jms-säije, niin tämä jumittaisi. (esim. testeissä)
    (when-not (get @jms-saije-sammutettu? (:nimi this))
      (async/>!! saikeen-sammutus-kanava true))
    ;; Odotetaan, että käsitteillä olevat viestit on käsitelty
    (async/<!! (async/go-loop []
                 (when (not (get @jms-saije-sammutettu? (:nimi this)))
                   (< (async/timeout 1000))
                   (recur))))
    (assoc this :tila nil
                :jms-connection-tila nil
                :yhteys-aloitettu? nil
                :yhteys-future nil
                :kaskytyskanava nil
                :lopeta-tarkkailu-kanava nil
                :saikeen-sammutus-kanava nil))
  JMS
  (kuuntele! [this jonon-nimi kuuntelija-fn jarjestelma]
    (if (some? jonon-nimi)
      (do
        (swap! (:tila this) update-in [:istunnot jarjestelma :jonot jonon-nimi]
               (fn [{kuuntelijat :kuuntelijat}]
                 {:kuuntelijat (conj (or kuuntelijat #{}) kuuntelija-fn)}))
        #(laheta-viesti-kaskytyskanavaan! (:kaskytyskanava this)
                                          {:poista-kuuntelija [jarjestelma jonon-nimi kuuntelija-fn]}
                                          (:nimi this)))
      (do
        (log/warn (str "jonon nimeä ei annettu, JMS-jonon kuuntelijaa ei käynnistetä järejestelmälle: " (:nimi this)))
        (constantly nil))))
  (kuuntele! [this jonon-nimi kuuntelija-fn]
    (kuuntele! this jonon-nimi kuuntelija-fn (oletusjarjestelmanimi jonon-nimi)))

  (laheta [{kaskytyskanava :kaskytyskanava :as this} jonon-nimi viesti otsikot jarjestelma]
    (let [lahetyksen-viesti (async/<!! (laheta-viesti-kaskytyskanavaan! kaskytyskanava
                                                                        {:laheta-viesti [jonon-nimi viesti otsikot jarjestelma]}
                                                                        (:nimi this)))]
      (cond
        (contains? lahetyksen-viesti :virhe) (throw (:virhe lahetyksen-viesti))
        (contains? lahetyksen-viesti :kaskytysvirhe) (case (:kaskytysvirhe lahetyksen-viesti)
                                                       :jms-saije-sammutettu (throw+ jms-saije-sammutettu-virhe)
                                                       :jms-saijetta-sammutetaan (throw+ jms-saije-sammutetaan-virhe)
                                                       :kasykytyskanava-taynna (throw+ kasykytyskanava-taynna-virhe)
                                                       :aikakatkaisu (throw+ aikakatkaisu-virhe)))
      :else (:vastaus lahetyksen-viesti)))
  (laheta [this jonon-nimi viesti otsikot]
    (laheta this jonon-nimi viesti otsikot (oletusjarjestelmanimi jonon-nimi)))
  (laheta [this jonon-nimi viesti]
    (laheta this jonon-nimi viesti nil (oletusjarjestelmanimi jonon-nimi)))
  (sammuta-lahettaja [this jonon-nimi jarjestelma]
    (laheta-viesti-kaskytyskanavaan! (:kaskytyskanava this)
                                     {:poista-lahettaja [jarjestelma jonon-nimi]}
                                     (:nimi this)))
  (sammuta-lahettaja [this jonon-nimi]
    (sammuta-lahettaja this jonon-nimi (oletusjarjestelmanimi jonon-nimi)))
  (kasky [{:keys [kaskytyskanava nimi]} kaskyn-tiedot]
    (laheta-viesti-kaskytyskanavaan! kaskytyskanava kaskyn-tiedot nimi))
  kp/IStatus
  (-status [this]
    (let [status (jms-client-tila @(:tila this) (:jms-connection-tila this))]
      {::kp/kaikki-ok? (jmsyhteys-ok? status)
       ::kp/tiedot {(:nimi this) status}})))

(defn yhteyden-tila [yhteys]
  (exception-wrapper yhteys getClientID))

(defn istunnon-tila
  [istunto]
  (exception-wrapper istunto getAcknowledgeMode))

(defn tuottajan-tila [tuottaja]
  (exception-wrapper tuottaja getDeliveryMode))

(defn vastaanottajan-tila [vastaanottaja]
  (exception-wrapper vastaanottaja getMessageListener))

(defn jms-jono-ok?
  ([jms-client jonon-nimi] (jms-jono-ok? jms-client jonon-nimi (oletusjarjestelmanimi jonon-nimi)))
  ([jms-client jonon-nimi jarjestelma]
   (let [jms-tila (-> jms-client :tila deref)
         {:keys [jonot istunto]} (-> jms-tila :istunnot (get jarjestelma))
         {:keys [tuottaja vastaanottaja]} (get jonot jonon-nimi)

         yhteys-ok? (= aktiivinen (yhteyden-tila (:yhteys jms-tila)))
         istunto-ok? (= aktiivinen (istunnon-tila istunto))
         jono-ok? (boolean (cond-> (or tuottaja vastaanottaja)
                                   tuottaja (and (= aktiivinen (tuottajan-tila tuottaja)))
                                   vastaanottaja (and (= aktiivinen (vastaanottajan-tila vastaanottaja)))))]
     (and yhteys-ok? istunto-ok? jono-ok?))))

(defn jms-jono-olemassa?
  ([jms-client jonon-nimi] (jms-jono-ok? jms-client jonon-nimi (oletusjarjestelmanimi jonon-nimi)))
  ([jms-client jonon-nimi jarjestelma]
   (boolean (-> jms-client :tila deref :istunnot (get jarjestelma) :jonot (get jonon-nimi)))))

(defn jms-jonolla-kuuntelija?
  ([jms-client jonon-nimi f-meta] (jms-jonolla-kuuntelija? jms-client jonon-nimi (oletusjarjestelmanimi jonon-nimi) f-meta))
  ([jms-client jonon-nimi jarjestelma f-meta]
   (let [jms-tila (-> jms-client :tila deref)
         {:keys [jonot]} (-> jms-tila :istunnot (get jarjestelma))
         {:keys [kuuntelijat]} (get jonot jonon-nimi)]
     (boolean (some (fn [f]
                      (= (-> f meta :jms-kuuntelija) f-meta))
                    kuuntelijat)))))

(defn luo-feikki-jms []
  (reify
    component/Lifecycle
    (start [this] this)
    (stop [this] this)

    JMS
    (kuuntele! [this jonon-nimi kuuntelija-fn jarjestelma]
      (log/debug "Feikki JMS, aloita muka kuuntelu jonossa: " jonon-nimi)
      (constantly nil))
    (kuuntele! [this jonon-nimi kuuntelija-fn]
      (kuuntele! this jonon-nimi kuuntelija-fn nil))
    (laheta [this jonon-nimi viesti otsikot jarjestelma]
      (log/debug "Feikki JMS, lähetä muka viesti jonoon: " jonon-nimi)
      (str "ID:" (System/currentTimeMillis)))
    (laheta [this jonon-nimi viesti otsikot]
      (laheta this jonon-nimi viesti otsikot nil))
    (laheta [this jonon-nimi viesti]
      (laheta this jonon-nimi viesti nil nil))
    (sammuta-lahettaja [this jonon-nimi jarjestelma]
      (log/debug "Feikki JMS samuttaa muka viesti jonon: " jonon-nimi))
    (sammuta-lahettaja [this jonon-nimi]
      (sammuta-lahettaja this jonon-nimi (oletusjarjestelmanimi jonon-nimi)))
    (kasky [this kaskyn-tiedot]
      (log/debug "Feikki JMS sai käskyn"))
    kp/IStatus
    (-status [this]
      (log/debug "Feikki JMS tila")
      {::kp/kaikki-ok? true
       ::kp/tiedot true})))