(ns harja.palvelin.komponentit.sonja
  "Komponentti Sonja-väylän JMS-jonoihin liittymiseksi."
  (:require [com.stuartsierra.component :as component]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [cheshire.core :as cheshire]
            [clojure.core.async :refer [go-loop go <! >! thread >!! <!! timeout chan] :as async]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.kyselyt.harjatila :as q]
            [harja.fmt :as fmt])
  (:import (javax.jms Session ExceptionListener JMSException MessageListener)
           (java.lang.reflect Proxy InvocationHandler)
           (java.net InetAddress))
  (:use [slingshot.slingshot :only [try+ throw+]]))

(def JMS-alkutila
  {:yhteys nil :istunnot {}})

(def ei-jms-yhteytta {:type :jms-yhteysvirhe
                      :virheet [{:koodi :ei-yhteytta
                                 :viesti "Sonja yhteyttä ei saatu. Viestiä ei voida lähettää."}]})
(defprotocol LuoViesti
  (luo-viesti [x istunto]))

(extend-protocol LuoViesti
  String
  (luo-viesti [s istunto]
    (doto (.createTextMessage istunto)
      (.setText s)))
  ;; Luodaan multipart viesti
  clojure.lang.PersistentArrayMap
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

(defprotocol Sonja
  (kuuntele! [this jonon-nimi kuuntelija-fn] [this jonon-nimi kuuntelija-fn jarjestelma]
    "Lisää uuden kuuntelijan annetulle jonolle. Jos jonolla on monta kuuntelijaa,
    viestit välitetään jokaiselle kuuntelijalle.
    Kuuntelijafunktiolle annetaan suoraan javax.jms.Message objekti.
    Kuuntelija blokkaa käsittelyn ajan, joten samasta jonosta voidaan lukea vain yksi viesti kerrallaan.
    Kuuntelija ei saa luoda uutta säijettä, koska AUTO_ACKNOWLEDGE on päällä. Tämä tarkoittaa sitä, että jos viestin
    käsittely epäonnistuu uudessa säikeessä ja varsinainen consumer on lopettanut jo hommansa, niin viesti on jo poistettu
    jonosta.")

  (laheta [this jono viesti] [this jono viesti otsikot] [this jono viesti otsikot jarjestelma]
    "Lähettää viestin nimettyyn jonoon. Palauttaa message id:n.")

  (aloita-yhteys [this]
    "Aloita sonja yhteys"))

(def jms-driver-luokka {:activemq "org.apache.activemq.ActiveMQConnectionFactory"
                        :sonicmq "progress.message.jclient.QueueConnectionFactory"})

(defmacro exception-wrapper [olio metodi]
  `(when ~olio
     (try (. ~olio ~metodi)
          "ACTIVE"
          ~(list 'catch 'javax.jms.IllegalStateException 'e
                 "CLOSED"))))

(defn hae-jonon-viestit [istunto jono]
  (when (and istunto jono)
    (let [selailija (.createBrowser istunto jono)]
      (try (loop [viesti-elementit (.getEnumeration selailija)
                  elementit []]
             (if (.hasMoreElements viesti-elementit)
               (let [elementti (-> viesti-elementit .nextElement)]
                 (recur viesti-elementit
                        (conj elementit elementti)))
               elementit))
           (catch JMSException e
             nil)
           (finally
             (when selailija
               (.close selailija)))))))

(defn aloita-sonja-yhteyden-tarkkailu [kaskytys-kanava tyyppi db]
  (go-loop []
    (>! kaskytys-kanava {:jms-tilanne [tyyppi db]})
    (<! (timeout 5000))
    (recur)))

(declare aloita-yhdistaminen
         yhdista-kuuntelija
         tee-jms-poikkeuskuuntelija)

(defn- poista-consumerit [{jonot :jonot :as tila}]
  (reduce (fn [tila jono]
            (assoc-in tila [:jonot jono :consumer] nil))
          tila
          (keys jonot)))



#_(defn- luodaanko-uusi-sonja-yhteys? [kulunut-aika {:keys [yhteys jonot] :as tila} {:keys [tyyppi]}]
    (let [yhteyden-tila (when (= :sonicmq tyyppi)
                          (.getConnectionState yhteys))
          #_#_jonojen-tila (when)]
      (if (> kulunut-aika 120)
        true
        )))

#_(defn yhdista-uudelleen [{:keys [yhteys jonot] :as tila} JMS-oliot asetukset yhteys-ok?]
    (let [katkos-alkoi (pvm/nyt-suomessa)
          nukkumisaika 5000]
      (log/info "Yritetään yhdistään JMS-yhteys uudelleen. Katkos alkoi: " (str katkos-alkoi))
      (loop [kulunut-aika (pvm/aikavali-sekuntteina katkos-alkoi (pvm/nyt-suomessa))
             kaynnistetaan-uudestaan? (luodaanko-uusi-sonja-yhteys? kulunut-aika @JMS-oliot asetukset)]
        (if kaynnistetaan-uudestaan?
          (do
            (when yhteys
              (try
                (.close yhteys)
                (catch Exception e
                  (log/error e "JMS-yhteyden sulkemisessa tapahtui poikkeus: " (.getMessage e)))))
            (loop [tila (aloita-yhdistaminen (poista-consumerit tila) asetukset (tee-jms-poikkeuskuuntelija JMS-oliot asetukset yhteys-ok?) yhteys-ok?)
                   [[jonon-nimi kuuntelija] & kuuntelijat]
                   (mapcat (fn [[jonon-nimi {kuuntelijat :kuuntelijat}]]
                             (log/info (format "Yhdistetään uudestaan kuuntelijat jonoon: %s" jonon-nimi))
                             (map (fn [k] [jonon-nimi k]) @kuuntelijat))
                           jonot)]
              (if (not jonon-nimi)
                tila
                (recur (yhdista-kuuntelija tila jonon-nimi kuuntelija)
                       kuuntelijat))))
          (do (Thread/sleep nukkumisaika)
              (recur (pvm/aikavali-sekuntteina katkos-alkoi (pvm/nyt-suomessa))
                     (luodaanko-uusi-sonja-yhteys? kulunut-aika @JMS-oliot asetukset)))))))

(defn tee-sonic-jms-tilamuutoskuuntelija []
  (let [lokita-tila #(case %
                       0 (log/info "Sonja JMS yhteyden tila: ACTIVE")
                       1 (log/info "Sonja JMS yhteyden tila: RECONNECTING")
                       2 (log/error "Sonja JMS yhteyden tila: FAILED")
                       3 (log/info "Sonja JMS yhteyden tila: CLOSED"))
        kasittelija (reify InvocationHandler (invoke [_ _ _ args] (lokita-tila (first args))))
        luokka (Class/forName "progress.message.jclient.ConnectionStateChangeListener")
        instanssi (Proxy/newProxyInstance (.getClassLoader luokka) (into-array Class [luokka]) kasittelija)]
    instanssi))

(defn tee-jms-poikkeuskuuntelija [JMS-oliot asetukset yhteys-ok?]
  (reify ExceptionListener
    (onException [_ e]
      (log/error e (str "Tapahtui JMS-poikkeus: " (.getMessage e)))

      #_(send-off JMS-oliot yhdista-uudelleen JMS-oliot asetukset yhteys-ok?))))

(defn konfiguroi-sonic-jms-connection-factory [connection-factory]
  (doto connection-factory
    (.setFaultTolerant true)
    (.setFaultTolerantReconnectTimeout (int 300))))

(defn- luo-connection-factory [url tyyppi]
  (let [connection-factory (-> tyyppi
                               jms-driver-luokka
                               Class/forName
                               (.getConstructor (into-array Class [String]))
                               (.newInstance (into-array Object [url])))]
    (if (= tyyppi :activemq)
      connection-factory
      (konfiguroi-sonic-jms-connection-factory connection-factory))))

(defn- yhdista [{:keys [kayttaja salasana tyyppi] :as asetukset} poikkeuskuuntelija qcf aika]
  (try
    (let [yhteys (.createQueueConnection qcf kayttaja salasana)]
      (when (= tyyppi :sonicmq)
        (.setConnectionStateChangeListener yhteys (tee-sonic-jms-tilamuutoskuuntelija)))
      (.setExceptionListener yhteys poikkeuskuuntelija)
      yhteys)
    (catch JMSException e
      (log/warn (format "Ei saatu yhteyttä Sonjan JMS-brokeriin. Yritetään uudestaan %s millisekunnin päästä. " aika) e)
      (Thread/sleep aika)
      (yhdista asetukset poikkeuskuuntelija qcf (min (* 2 aika) 600000)))
    (catch Exception e
      (log/error "JMS brokeriin yhdistäminen epäonnistui: " e)
      nil)))

(defn aloita-yhdistaminen [{:keys [url tyyppi] :as asetukset} poikkeuskuuntelija]
  (log/info "Yhdistetään " (if (= tyyppi :activemq) "ActiveMQ" "Sonic") " JMS-brokeriin URL:lla:" url)
  (let [qcf (luo-connection-factory url tyyppi)
        yhteys (yhdista asetukset poikkeuskuuntelija qcf 10000)]
    (when (= :sonicmq tyyppi)
      (log/info "Yhteyden metadata: " (when-let [meta-data (.getMetaData yhteys)]
                                        meta-data)))
    (when yhteys
      (do
        (log/info "Saatiin yhteys Sonjan JMS-brokeriin.")
        {:yhteys yhteys :qcf qcf}))))

(defn kasittele-viesti [vastaanottaja kuuntelijat tila jarjestelma jonon-nimi]
  (.setMessageListener vastaanottaja
                       (with-meta
                         (reify MessageListener
                           (onMessage [_ message]
                             (doseq [kuuntelija kuuntelijat]
                               (try
                                 (kuuntelija message)
                                 (catch Throwable t
                                   (log/error (str "Jonoon " (-> vastaanottaja .getQueue .getQueueName) " tuli viesti "
                                                  (if (instance? javax.jms.TextMessage message)
                                                    (.getText message)
                                                    message)
                                                  " ja sen käsittely epäonnistui funktiolta " kuuntelija))
                                   (swap! tila update-in [:istunnot jarjestelma :jonot jonon-nimi :virheet]
                                          (fn [virheet]
                                            (conj (or virheet []) {:viesti (.getMessage t)
                                                                   :aika (pvm/nyt-suomessa)})))
                                   (throw t))))))
                         {:kuuntelijoiden-maara (count kuuntelijat)})))

(defn poista-kuuntelija [tila jarjestelma jonon-nimi kuuntelija-fn]
  (let [{:keys [jonot istunto]} (get-in @tila [:istunnot jarjestelma])
        {:keys [kuuntelijat vastaanottaja jono] :as jonon-tiedot} (get jonot jonon-nimi)]
    (let [kuuntelijat (disj kuuntelijat kuuntelija-fn)]
      ;; Jos viestiä käsitellään, kun vaihdetaan messageListeneriä, niin sen seuraukset ovat määrittelemättömät.
      ;; Sen takia ensin sammutetaan nykyinen kuuntelija, koska se ensin käsittelee käsitteilä olevat viestit ja blokkaa siksi aikaa.
      ;; Tämän jälkeen luodaan uusi vastaanottaja.
      (.close vastaanottaja)
      (if (empty? kuuntelijat)
        (swap! tila assoc-in [:istunnot jarjestelma :jonot jonon-nimi] nil)
        (let [vastaanottaja (.createReceiver istunto jono)]
          (kasittele-viesti vastaanottaja kuuntelijat tila jarjestelma jonon-nimi)
          (swap! tila update-in [:jonot jonon-nimi]
                 (fn [jonon-tiedot]
                   (assoc jonon-tiedot :vastaanottaja vastaanottaja
                                       :kuuntelijat kuuntelijat))))))))

(defn luo-istunto [yhteys jonon-nimi]
  (.createQueueSession yhteys false Session/AUTO_ACKNOWLEDGE))

(defn varmista-jms-objektit [tila jonon-nimi jarjestelma kasittelija]
  (let [{istunnot :istunnot yhteys :yhteys} @tila
        {istunto :istunto jonot :jonot} (get istunnot jarjestelma)
        {jono :jono kasittelija-olio kasittelija kuuntelijat :kuuntelijat} (get jonot jonon-nimi)]
    (when-not (and istunto jono kasittelija-olio)
      (let [istunto (or istunto (luo-istunto yhteys jonon-nimi))
            jono (or jono (.createQueue istunto jonon-nimi))
            kasittelija-olio (or kasittelija-olio (if (= kasittelija :vastaanottaja)
                                                    (let [vastaanottaja (.createReceiver istunto jono)]
                                                      (kasittele-viesti vastaanottaja kuuntelijat tila jarjestelma jonon-nimi)
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
               (conj (or virheet []) {:viesti (.getMessage e)
                                      :aika (pvm/nyt-suomessa)})))
      (log/error e "Virhe JMS-viestin lähettämisessä jonoon: " jonon-nimi))))

(defn luo-jms-saije
  "JMS spesifikaation mukaan connection oliota voi käyttää ihan vapaasti useasta eri säikeestä, mutta session olioita ja
   kaikkia sen luomia olioita ei voi. Niitä tulisi kohdella säijekohtaisesti. Mikään ei estä niiden käsittelyä useasta eri
   säikeestä, mutta se on käyttäjän vastuulla, että samaa oliota ei käytetä yhtä aikaa, jonka takia tosiaa suositellaan,
   että manipulaatio tehdään yhdestä säikeestä. Tässä luodaan vain yksi säije, jossa luodaan kaikki sessiot ja siihen
   liittyvät muut oliot. Myöhemmin voi luoda useampia säikeitä joihin tulee vaikkapa vain tietyn järjestelmän sessiot, jos
   tähän on tarvesta. ActiveMQ ja SonicMQ ovat kumminkin implementoitu niin, että useamman session luominen johtaa jo
   threadpoolien käyttämiseen eikä kaikkea varsinaisesti suoriteta yhdessä säikeessä.

   Näitä jms-olioita käsitellään omassa säikeessään 'pääsäikeen' sijasta, jotta Harja järjestelmä voidaan muuten käynnistää
   vaikka olisi jotain ongelmia Sonjan kanssa."
  [{:keys [tila yhteys-ok? yhteys-future kaskytys-kanava]}]
  (thread
    (doto (Thread/currentThread)
      (.setName "jms-saije"))
    ;; Ensin varmistetaan, että yhteys sonjaan on saatu. futuren dereffaaminen blokkaa, kunnes saadaan
    ;; joku arvo pihalle.
    (let [yhteys-olio @yhteys-future]
      (swap! tila merge yhteys-olio)
      (loop []
        (let [[kasky params] (first (<!! kaskytys-kanava))]
          (try (case kasky
                 :aloita-yhteys (let [{:keys [istunnot yhteys]} @tila]
                                  ;; Alustetaan vastaanottaja jvm oliot
                                  (doseq [[jarjestelma {jonot :jonot}] istunnot]
                                    (doseq [[jonon-nimi _] jonot]
                                      (varmista-jms-objektit tila jonon-nimi jarjestelma :vastaanottaja)))
                                  ;; Aloita yhteys
                                  (.start yhteys)
                                  (reset! yhteys-ok? true))
                 :laheta-viesti (let [[jonon-nimi viesti {:keys [correlation-id]} jarjestelma] params]
                                  (varmista-jms-objektit tila jonon-nimi jarjestelma :tuottaja)
                                  ;; Meidän ei tarvitse olla varmoja, että yhteys on aloitettu (eli start metodi on kutsuttu) silloin kun
                                  ;; lähetetään viestejä. JMS jonoissa se ei ole pakollista.
                                  (laheta-viesti tila jonon-nimi viesti correlation-id jarjestelma))
                 :jms-tilanne (let [[tyyppi db] params
                                    {:keys [yhteys istunnot]} @tila
                                    yhteyden-tila (when yhteys
                                                    (if (= tyyppi :activemq)
                                                      (if (.isClosed yhteys)
                                                        "CLOSED"
                                                        "ACTIVE")
                                                      (.getConnectionState yhteys)))
                                    olioiden-tilat {:yhteyden-tila yhteyden-tila
                                                    :istunnot (mapv (fn [[jarjestelma istunto-tiedot]]
                                                                      ;; SonicMQ API:n avulla ei voi tarkistella suoraan onko sessio, vastaanottaja tai tuottaja sulettu,
                                                                      ;; joten täytyy yrittää käyttää sitä objektia johonkin ja katsoa nakataanko IllegalStateException
                                                                      (let [{:keys [jonot istunto]} istunto-tiedot
                                                                            istunnon-tila (exception-wrapper istunto getAcknowledgeMode)]
                                                                        {:istunnon-tila istunnon-tila
                                                                         :jonot (map (fn [[jonon-nimi {:keys [jono tuottaja vastaanottaja virheet]}]]
                                                                                       (let [jonon-viestit (hae-jonon-viestit istunto jono)
                                                                                             tuottajan-tila (exception-wrapper tuottaja getDeliveryMode)
                                                                                             vastaanottajan-tila (exception-wrapper vastaanottaja getMessageListener)]
                                                                                         {jonon-nimi {:jonon-viestit jonon-viestit
                                                                                                      :tuottaja (when tuottaja
                                                                                                                  {:tuottajan-tila tuottajan-tila
                                                                                                                   :virheet virheet})
                                                                                                      :vastaanottaja (when vastaanottaja
                                                                                                                       {:vastaanottajan-tila vastaanottajan-tila
                                                                                                                        :virheet virheet})}}))
                                                                                     jonot)}))
                                                                    istunnot)}]
                                (q/tallenna-sonjan-tila<! db {:tila (cheshire/encode olioiden-tilat)
                                                              :palvelin (fmt/leikkaa-merkkijono 512
                                                                                                (.toString (InetAddress/getLocalHost)))
                                                              :osa-alue "sonja"}))
                 nil)
               (catch Throwable t
                 (log/error "Jokin meni vikaan sonjakäskyissä: " t)))
          (recur))))))

#_(defn yhdista-kuuntelija [tila jarjestelma]
  (log/debug (format "Yhdistetään kuuntelija jonoon: %s. Tila: %s." jonon-nimi tila))
  (let [{yhteys :yhteys istunnot :istunnot} @tila
        istunto (luo-istunto yhteys)
        jono (.createQueue istunto jonon-nimi)
        vastaanottaja (.createReceiver istunto jono)]
    (kasittele-viesti vastaanottaja kuuntelijat tila jonon-nimi)
    (swap! tila update-in [:jonot jonon-nimi]
           (fn [jonon-tila]
             (assoc jonon-tila
               :istunto istunto
               :jono jono
               :vastaanottaja vastaanottaja)))))

(defrecord SonjaYhteys [asetukset tila yhteys-ok? yhteys-future]
  component/Lifecycle
  (start [{db :db
           :as this}]
    (let [JMS-oliot (atom JMS-alkutila)
          yhteys-ok? (atom false)
          kaskytys-kanava (chan 100)
          poikkeus-kuuntelija (tee-jms-poikkeuskuuntelija JMS-oliot asetukset yhteys-ok?)
          yhteys-future (future
                          (aloita-yhdistaminen asetukset poikkeus-kuuntelija))
          this (assoc this
                 :tila JMS-oliot
                 :yhteys-ok? yhteys-ok?
                 :yhteys-future yhteys-future
                 :kaskytys-kanava kaskytys-kanava)
          jms-saije (luo-jms-saije this)]
      (swap! (:tila this) assoc :jms-saije jms-saije)
      (assoc this
        :yhteyden-tiedot (aloita-sonja-yhteyden-tarkkailu kaskytys-kanava (:tyyppi asetukset) db))))

  (stop [this]
    (when @yhteys-ok?
      (let [tila @tila]
        (some-> tila :yhteys .close)))
    (assoc this
      :tila nil))

  Sonja
  (kuuntele! [this jonon-nimi kuuntelija-fn jarjestelma]
    (if (some? jonon-nimi)
      (do
        (swap! (:tila this) update-in [:istunnot jarjestelma :jonot jonon-nimi]
               (fn [{kuuntelijat :kuuntelijat}]
                 {:kuuntelijat (conj (or kuuntelijat #{}) kuuntelija-fn)}))
        #(poista-kuuntelija (:tila this) jarjestelma jonon-nimi kuuntelija-fn))
      (do
        (log/warn "jonon nimeä ei annettu, JMS-jonon kuuntelijaa ei käynnistetä")
        (constantly nil))))
  (kuuntele! [this jonon-nimi kuuntelija-fn]
    (kuuntele! this jonon-nimi kuuntelija-fn "muu"))

  (laheta [{kaskytys-kanava :kaskytys-kanava :as this} jonon-nimi viesti otsikot jarjestelma]
    (>!! kaskytys-kanava {:laheta-viesti [jonon-nimi viesti otsikot jarjestelma]}))
  (laheta [this jonon-nimi viesti otsikot]
    (laheta this jonon-nimi viesti otsikot "muu"))
  (laheta [this jonon-nimi viesti]
    (laheta this jonon-nimi viesti nil "muu"))

  (aloita-yhteys [{jms-saije :jms-saije kaskytys-kanava :kaskytys-kanava :as this}]
    (>!! kaskytys-kanava {:aloita-yhteys nil})))

(defn luo-oikea-sonja [asetukset]
  (->SonjaYhteys asetukset nil nil nil))

(defn luo-feikki-sonja []
  (reify
    component/Lifecycle
    (start [this] this)
    (stop [this] this)

    Sonja
    (kuuntele! [this jonon-nimi kuuntelija-fn jarjestelma]
      (log/debug "Feikki Sonja, aloita muka kuuntelu jonossa: " jonon-nimi)
      (constantly nil))
    (kuuntele! [this jonon-nimi kuuntelija-fn]
      (kuuntele! this jonon-nimi kuuntelija-fn nil))
    (laheta [this jonon-nimi viesti otsikot jarjestelma]
      (log/debug "Feikki Sonja, lähetä muka viesti jonoon: " jonon-nimi)
      (str "ID:" (System/currentTimeMillis)))
    (laheta [this jonon-nimi viesti otsikot]
      (laheta this jonon-nimi viesti nil nil))
    (laheta [this jonon-nimi viesti]
      (laheta this jonon-nimi viesti nil nil))
    (aloita-yhteys [this]
      (log/debug "Feikki Sonja, aloita muka yhteys"))))

(defn luo-sonja [asetukset]
  (if (and asetukset (not (str/blank? (:url asetukset))))
    (luo-oikea-sonja asetukset)
    (luo-feikki-sonja)))
