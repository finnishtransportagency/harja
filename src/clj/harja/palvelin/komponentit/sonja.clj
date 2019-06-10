(ns harja.palvelin.komponentit.sonja
  "Komponentti Sonja-väylän JMS-jonoihin liittymiseksi."
  (:require [com.stuartsierra.component :as component]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [cheshire.core :as cheshire]
            [clojure.core.async :refer [thread >!! <!! timeout chan] :as async]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.kyselyt.jarjestelman-tila :as q]
            [harja.fmt :as fmt]
            [slingshot.slingshot :refer [try+ throw+]])
  (:import (javax.jms Session ExceptionListener JMSException MessageListener)
    ;(java.io EOFException)
           (java.lang.reflect Proxy InvocationHandler)
           (java.net InetAddress)
    ;(java.util Enumeration Collections)
           ))

(defonce jms-saije-sammutettu? (atom true))

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

(def viestin-kasittely-timeout 5000)

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
    jonosta.
    Jos 'jarjestelma' on annettu, niin tässä määritetyn jonon viestit käsitellään samassa sessiossa kuin muut
    kuuntelijat ja lähettäjät samalla 'jarjestelma' nimellä.")

  (laheta [this jono viesti] [this jono viesti otsikot] [this jono viesti otsikot jarjestelma]
    "Lähettää viestin nimettyyn jonoon. Palauttaa message id:n.
    Jos 'jarjestelma' on annettu, niin tässä määritetyn jonon viestit käsitellään samassa sessiossa kuin muut
    kuuntelijat ja lähettäjät samalla 'jarjestelma' nimellä.")

  (aloita-yhteys [this]
    "Aloita sonja yhteys"))

(def jms-driver-luokka {:activemq "org.apache.activemq.ActiveMQConnectionFactory"
                        :sonicmq "progress.message.jclient.QueueConnectionFactory"})

(defmacro exception-wrapper [olio metodi]
  `(try (. ~olio ~metodi)
        "ACTIVE"
        ~(list 'catch 'javax.jms.IllegalStateException 'e
               "CLOSED")
        ~(list 'catch 'Throwable 't
               nil)))

(defn hae-jonon-viestit [istunto jono]
  "Luodaan selailija olio aina uudestaan, koska se saa jonosta snapshot tilanteen silloin, kun se luodaan."
  (when (and istunto jono)
    (try (let [selailija (.createBrowser istunto jono)
               viesti-elementit (.getEnumeration selailija)]
           (loop [elementit []]
             ;; Selailija olio ei ainakaan ActiveMQ:n kanssa toimi oikein kun istunnossa on useita eri jonoja. hasMoreElements
             ;; palauttaa oikein true sillin, kun siellä on viestejä, mutta nextElement palauttaa siitä huolimatta nil
             (if (.hasMoreElements viesti-elementit)
               (let [elementti (.nextElement viesti-elementit)]
                 (if (nil? elementti)
                   (conj elementit nil)
                   (recur (conj elementit elementti))))
               (do
                 (.close selailija)
                 elementit))))
         (catch JMSException e
           nil))))

(declare tee-jms-poikkeuskuuntelija
         laheta-viesti-kaskytyskanavaan!
         jms-toiminto!)

(defn aloita-sonja-yhteyden-tarkkailu [kaskytyskanava lopeta-tarkkailu-kanava tyyppi db]
  (thread
    (doto (Thread/currentThread)
      (.setName "jms-yhteyden-tarkkailu-saije"))
    (loop [[lopetetaan? _] (async/alts!! [lopeta-tarkkailu-kanava]
                                         :default false)]
      (when-not lopetetaan?
        (laheta-viesti-kaskytyskanavaan! kaskytyskanava {:jms-tilanne [tyyppi db]})
        (<!! (timeout 5000))
        (recur (async/alts!! [lopeta-tarkkailu-kanava]
                             :default false))))))

(defn yhdista-uudelleen [{:keys [tila yhteys-ok? kaskytyskanava] :as sonja}]
  (let [katkos-alkoi (pvm/nyt-suomessa)
        nukkumisaika 5000
        {yhteys :yhteys} @tila]
    (log/info "Yritetään yhdistään JMS-yhteys uudelleen. Katkos alkoi: " (str katkos-alkoi))
    (when yhteys
      (try
        (.close yhteys)
        (reset! yhteys-ok? false)
        (catch Exception e
          (log/error e "JMS-yhteyden sulkemisessa tapahtui poikkeus: " (.getMessage e))
          (reset! yhteys-ok? false))))
    (thread
      (doto (Thread/currentThread)
        (.setName "jms-reconnecting-saije"))
      (loop [{:keys [vastaus virhe kaskytysvirhe]} (<!! (laheta-viesti-kaskytyskanavaan! kaskytyskanava {:yhdista-uudelleen nil}))]
        (cond
          (or virhe
              (= :kasykytyskanava-taynna kaskytysvirhe)) (recur (<!! (laheta-viesti-kaskytyskanavaan! kaskytyskanava {:yhdista-uudelleen nil})))
          kaskytysvirhe  (log/info "Sonjaa ei käynnistetä uudelleen, sillä jms-saije sammutetaan")
          vastaus (log/info "Sonja jonot pystytetty uudestaan. Aikaa meni: " (pvm/aikavali-sekuntteina katkos-alkoi (pvm/nyt-suomessa))))))))

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

(defn tee-jms-poikkeuskuuntelija [sonja]
  (reify ExceptionListener
    (onException [_ e]
      (log/error e (str "Tapahtui JMS-poikkeus: " (.getMessage e)))
      (yhdista-uudelleen sonja))))

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

(defn luo-istunto [yhteys]
  (.createQueueSession yhteys false Session/AUTO_ACKNOWLEDGE))

(defn- yhdista [{:keys [kayttaja salasana tyyppi] :as asetukset} qcf aika]
  (try
    (let [yhteys (.createQueueConnection qcf kayttaja salasana)]
      (when (= tyyppi :sonicmq)
        (.setConnectionStateChangeListener yhteys (tee-sonic-jms-tilamuutoskuuntelija)))
      yhteys)
    (catch JMSException e
      (log/warn (format "Ei saatu yhteyttä Sonjan JMS-brokeriin. Yritetään uudestaan %s millisekunnin päästä. " aika) e)
      (Thread/sleep aika)
      (yhdista asetukset qcf (min (* 2 aika) 600000)))
    (catch Throwable t
      (log/error "JMS brokeriin yhdistäminen epäonnistui: " (.getMessage t) "\nStackTrace: " (.printStackTrace t))
      (Thread/sleep aika)
      (yhdista asetukset qcf (min (* 2 aika) 600000)))))

(defn aloita-yhdistaminen [{:keys [url tyyppi] :as asetukset}]
  (log/info "Yhdistetään " (if (= tyyppi :activemq) "ActiveMQ" "Sonic") " JMS-brokeriin URL:lla:" url)
  (let [qcf (luo-connection-factory url tyyppi)
        yhteys (yhdista asetukset qcf 10000)]
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
        ;; ActiveMQ saattaa ainakin saada yhteyden borkeriin vaikkei yhteys olisi ok
        (log/error "Jokin meni vikaan, kun yritettiin saada yhteys Sonjaan... Yritetään yhdistää uudelleen.")
        ;; Yhteys objekti kummiskin pitää sammuttaa, jotta ylimääräiset säikeet sammutetaan ja muistia vapautetaan
        (try (.close yhteys)
             (catch Throwable t
               (log/debug "EI saatu sulettua epäonnistunutta yhteyttä")))
        (<!! (timeout 10000))
        (aloita-yhdistaminen asetukset)))))

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
                                                   (if (instance? javax.jms.TextMessage message)
                                                     (.getText message)
                                                     message)
                                                   " ja sen käsittely epäonnistui funktiolta " kuuntelija
                                                   " virheeseen " (.getMessage t)
                                                   "\nStackTrace: " (.printStackTrace t)))
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
      (let [istunto (or istunto (luo-istunto yhteys))
            jono (or jono (.createQueue istunto jonon-nimi))
            kasittelija-olio (or kasittelija-olio (if (= kasittelija :vastaanottaja)
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
                                       :aika (pvm/nyt-suomessa)})))))
      (log/error e "Virhe JMS-viestin lähettämisessä jonoon: " jonon-nimi))))

(defn yhteys-oliot!
  "Ilmoittaa omasta tilasta, kun yritetään yhdistää brokeriin."
  [yhteys-future {db :db {tyyppi :tyyppi} :asetukset :as sonja}]
  (jms-toiminto! sonja {:jms-tilanne [tyyppi db]})
  (let [[yhteys-oliot _] (async/alts!! [(timeout 5000) (thread @yhteys-future)])]
    (if yhteys-oliot
      yhteys-oliot
      (yhteys-oliot! yhteys-future sonja))))

(defmulti jms-toiminto!
  (fn [sonja kasky]
    (-> kasky keys first)))

(defmethod jms-toiminto! :aloita-yhteys
  [{:keys [tila yhteys-ok?] :as sonja} _]
  (try (let [{:keys [istunnot yhteys]} @tila
             poikkeuskuuntelija (tee-jms-poikkeuskuuntelija sonja)]
         ;; Alustetaan vastaanottaja jvm oliot
         (doseq [[jarjestelma {jonot :jonot}] istunnot]
           (doseq [[jonon-nimi _] jonot]
             (varmista-jms-objektit! tila jonon-nimi jarjestelma :vastaanottaja)))
         ;; Lisätään poikkeuskuuntelija yhteysolioon
         (.setExceptionListener yhteys poikkeuskuuntelija)
         ;; Aloita yhteys
         (log/debug "Aloitetaan yhteys")
         (.start yhteys)
         (reset! yhteys-ok? true)
         true)
       (catch Exception e
         (log/error "VIRHE TAPAHTUI :aloita-yhteys " (.getMessage e) "\nStackTrace: " (.printStackTrace e))
         {:virhe e})))

(defmethod jms-toiminto! :lopeta-yhteys
  [{:keys [tila yhteys-ok?] :as sonja} _]
  (try (let [{:keys [yhteys]} @tila]
         (log/debug "Lopetetaan yhteys")
         (.close yhteys)
         (reset! yhteys-ok? false)
         true)
       (catch Exception e
         (log/error "VIRHE TAPAHTUI :lopeta-yhteys " (.getMessage e) "\nStackTrace: " (.printStackTrace e))
         {:virhe e})))

(defmethod jms-toiminto! :yhdista-uudelleen
  [{:keys [asetukset tila kaskytyskanava] :as sonja} kasky]
  (try (let [yhteys-future (future (aloita-yhdistaminen asetukset))
             yhteys-olio (yhteys-oliot! yhteys-future sonja)]
         (swap! tila merge yhteys-olio)
         ;; Tässä tiputetaan kaikki jms-oliot pois, jotta ne voidaan luoda uudestaan.
         (swap! tila update :istunnot (fn [istunnot]
                                        (reduce (fn [tulos [istunnon-nimi m]]
                                                  (let [jonot (:jonot m)]
                                                    (merge tulos
                                                           {istunnon-nimi
                                                            {:jonot (into {}
                                                                          (map (fn [[jonon-nimi jms-oliot]]
                                                                                 [jonon-nimi (select-keys jms-oliot [:kuuntelijat])])
                                                                               jonot))}})))
                                                {} istunnot)))
         (jms-toiminto! sonja {:aloita-yhteys nil}))
       (catch Exception e
         (log/error "VIRHE TAPAHTUI :yhdista-uudelleen " (.getMessage e) "\nStackTrace: " (.printStackTrace e))
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
         (log/error "VIRHE TAPAHTUI :laheta-viesti " (.getMessage e) "\nStackTrace: " (.printStackTrace e))
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
         ;; Sen takia ensin sammutetaan nykyinen kuuntelija, koska se ensin käsittelee käsitteilä olevat viestit ja blokkaa siksi aikaa.
         ;; Tämän jälkeen luodaan uusi vastaanottaja.
         (.close vastaanottaja)
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
         (log/error "VIRHE TAPAHTUI :poista-kuuntelija " (.getMessage e) "\nStackTrace: " (.printStackTrace e))
         {:virhe e})))

(defmethod jms-toiminto! :jms-tilanne
  [{:keys [tila]} kasky]
  (try (let [params (when-let [params (vals kasky)]
                      (first params))
             [tyyppi db] params
             {:keys [yhteys istunnot]} @tila
             yhteyden-tila (when yhteys
                             (if (= tyyppi :activemq)
                               (if (.isClosed yhteys)
                                 "CLOSED"
                                 "ACTIVE")
                               (case (.getConnectionState yhteys)
                                 0 "ACTIVE"
                                 1 "RECONNECTING"
                                 2 "FAILED"
                                 3 "CLOSED"
                                 (str (.getConnectionState yhteys)))))
             olioiden-tilat {:yhteyden-tila yhteyden-tila
                             :istunnot (mapv (fn [[jarjestelma istunto-tiedot]]
                                               ;; SonicMQ API:n avulla ei voi tarkistella suoraan onko sessio, vastaanottaja tai tuottaja sulettu,
                                               ;; joten täytyy yrittää käyttää sitä objektia johonkin ja katsoa nakataanko IllegalStateException
                                               (let [{:keys [jonot istunto]} istunto-tiedot
                                                     istunnon-tila (exception-wrapper istunto getAcknowledgeMode)]
                                                 {:istunnon-tila istunnon-tila
                                                  :jarjestelma jarjestelma
                                                  :jonot (mapv (fn [[jonon-nimi {:keys [jono tuottaja vastaanottaja virheet]}]]
                                                                 (let [jonon-viestit (mapv #(when %
                                                                                              {:message-id (.getJMSMessageID %)
                                                                                               :timestamp (.getJMSTimestamp %)})
                                                                                           (hae-jonon-viestit istunto jono))
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
                                             istunnot)}
             saikeiden-tilat (sequence (comp
                                         (filter #(if (re-find #"^jms-(saije|kasittelyn-odottelija|reconnecting-saije)" (.getName %))
                                                    %))
                                         (map #(identity
                                                 {:nimi (.getName %)
                                                  :status (.. % getState toString)})))
                                       (.keySet (Thread/getAllStackTraces)))
             jms-tila {:olioiden-tilat olioiden-tilat
                       :saikeiden-tilat saikeiden-tilat}]
         (q/tallenna-sonjan-tila<! db {:tila (cheshire/encode jms-tila)
                                       :palvelin (fmt/leikkaa-merkkijono 512
                                                                         (.toString (InetAddress/getLocalHost)))
                                       :osa-alue "sonja"})
         jms-tila)
       (catch Exception e
         (log/error "VIRHE TAPAHTUI :jms-tilanne " (.getMessage e) "\nStackTrace: " (.printStackTrace e))
         {:virhe e})))

(defn kasittele-kasky!
  [{:keys [kasky kaskyn-kasittely-jms-saikeelle
           kaskyn-kasittely-kaskytys-saikeelle]}
   sonja]
  (let [;; Ilmoitetaan, että voidaan käsitellä käsky. Kumminkin on huomioitava, että kasittelykanavaan on saatettu jo lähettää aikakatkaisuviesti
        [viesti kanava] (async/alts!! [[kaskyn-kasittely-kaskytys-saikeelle :valmis-kasiteltavaksi] kaskyn-kasittely-jms-saikeelle])]
    (cond
      (= kanava kaskyn-kasittely-jms-saikeelle) (when (= viesti :aikakatkaisu)
                                                  nil)
      (= kanava kaskyn-kasittely-kaskytys-saikeelle) (case (<!! kaskyn-kasittely-jms-saikeelle)
                                                       :kasittele (let [vastaus (try (let [vastaus (jms-toiminto! sonja kasky)]
                                                                                       (if (and (map? vastaus)
                                                                                                (contains? vastaus :virhe)
                                                                                                (= (count vastaus) 1))
                                                                                         vastaus
                                                                                         {:vastaus vastaus}))
                                                                                     (catch Throwable t
                                                                                       (log/error "Jokin meni vikaan sonjakäskyissä: " (.getMessage t) "\nStackTrace: " (.printStackTrace t))
                                                                                       {:virhe t}))]
                                                                    ;(println "<---- VASTAUS: " vastaus)
                                                                    (>!! kaskyn-kasittely-kaskytys-saikeelle vastaus))))))

(defn luo-jms-saije
  "JMS spesifikaation mukaan connection oliota voi käyttää ihan vapaasti useasta eri säikeestä, mutta session olioita ja
   kaikkia sen luomia olioita ei voi. Niitä tulisi kohdella säijekohtaisesti. Mikään ei estä niiden käsittelyä useasta eri
   säikeestä, mutta se on käyttäjän vastuulla, että samaa oliota ei käytetä yhtä aikaa, jonka takia tosiaan suositellaan,
   että manipulaatio tehdään yhdestä säikeestä. Tässä luodaan vain yksi säije, jossa luodaan kaikki sessiot ja siihen
   liittyvät muut oliot. Myöhemmin voi luoda useampia säikeitä joihin tulee vaikkapa vain tietyn järjestelmän sessiot, jos
   tähän on tarvesta. ActiveMQ ja SonicMQ ovat kumminkin implementoitu niin, että useamman session luominen johtaa jo
   threadpoolien käyttämiseen eikä kaikkea varsinaisesti suoriteta yhdessä säikeessä.

   Näitä jms-olioita käsitellään omassa säikeessään 'pääsäikeen' sijasta, jotta Harja järjestelmä voidaan muuten käynnistää
   vaikka olisi jotain ongelmia Sonjan kanssa."
  [{:keys [yhteys-future tila kaskytyskanava] :as sonja} sammutus-kanava]
  (thread
    (reset! jms-saije-sammutettu? false)
    (doto (Thread/currentThread)
      (.setName "jms-saije"))
    ;; Ensin varmistetaan, että yhteys sonjaan on saatu. futuren dereffaaminen blokkaa, kunnes saadaan
    ;; joku arvo pihalle.
    (let [yhteys-oliot (yhteys-oliot! yhteys-future sonja)]
      (log/debug "Yhteys oliot valmiit")
      (swap! tila merge yhteys-oliot)
      ;; Aloitetaan ikuinen looppi
      (loop []
        (async/alt!!
          kaskytyskanava ([kasky _]
                           (println "KÄSKY OLI: ")
                           (clojure.pprint/pprint kasky)
                           (if (nil? kasky)
                             (do (log/info "Yritettiin antaa sammutetulle käskytyskanavalle käsky")
                                 (<!! (timeout 2000))
                                 (recur))
                             (do (kasittele-kasky! kasky sonja)
                                 (recur))))
          sammutus-kanava (do
                            ;; Sammutetaan käskytyskanava, jottei sinne voi tulla enää lisää käskyjä
                            (println "LOPETETAAN KANAVA")
                            (async/close! kaskytyskanava)
                            ;; Lopetuksen jälkeen käsitellään ensin kaikki kanavassa jo olevat
                            ;; käskyt
                            (loop [kasky (async/poll! kaskytyskanava)]
                              (println "KÄSKYTYSKANAVASTA SAATIIN VIELÄ: " kasky)
                              (if (nil? kasky)
                                (do
                                  (jms-toiminto! sonja {:lopeta-yhteys nil})
                                  (reset! jms-saije-sammutettu? true))
                                (do
                                  (kasittele-kasky! kasky sonja)
                                  (recur (async/poll! kaskytyskanava)))))))))))

(defn laheta-viesti-kaskytyskanavaan!
  "Lähettää käskyn jms-saikeelle. Jos käskytyskanava on täynnä, palauttaa virheen.
   Lisäksi käskyä ei käsitellä, jos timeout kerkeää mennä loppuun.
   Palauttaa kanavan, josta tuloksen voi lukea. Tulos voi olla käskyn käsitelty tulos tai sitten virhe-map, jossa ilmoitetaan
   epäonnisuiko käsittely timeoutin vai täyden bufferin takia taikka sammutetun jms-saikeen takia."
  [kaskytyskanava kasky]
  (thread
    (doto (Thread/currentThread)
      (.setName (str "jms-kasittelyn-odottelija*" (-> kasky keys first name) "*")))
    (let [kaskyn-kasittely-jms-saikeelle (chan)
          kaskyn-kasittely-kaskytys-saikeelle (chan)
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
               (timeout viestin-kasittely-timeout) (do (async/put! kaskyn-kasittely-jms-saikeelle
                                                                   :aikakatkaisu)
                                                       {:kaskytysvirhe :aikakatkaisu})
               kaskyn-kasittely-kaskytys-saikeelle ([tulos _]
                                                     (case tulos
                                                       :valmis-kasiteltavaksi (do
                                                                                (>!! kaskyn-kasittely-jms-saikeelle :kasittele)
                                                                                (<!! kaskyn-kasittely-kaskytys-saikeelle)))))
        ;; Käskytyskanava on sammutettu
        false (if (= :sammutettu @jms-saije-sammutettu?)
                {:kaskytysvirhe :jms-saije-sammutettu}
                {:kaskytysvirhe :jms-saijetta-sammutetaan})
        ;; Käskytyskanava on täynnä
        nil {:kaskytysvirhe :kasykytyskanava-taynna}))))

(defrecord SonjaYhteys [asetukset tila yhteys-ok?]
  component/Lifecycle
  (start [{db :db
           :as this}]
    (let [JMS-oliot (atom JMS-alkutila)
          yhteys-ok? (atom false)
          ;; HUOM! käskytyskanavaan ei tulisi laittaa viestejä muuten kuin laheta-viesti-kaskytyskanavaan!
          ;; funktion kautta.
          kaskytyskanava (chan 100)
          lopeta-tarkkailu-kanava (chan)
          saikeen-sammutus-kanava (chan)
          ;; Tämä futuressa sen takia, koska yhdistämisen aloittamien voi mahdollisesti loopata ikuisuuden eikä haluta
          ;; estää HARJA:n käynnistymistä sen takia.
          yhteys-future (future
                          (aloita-yhdistaminen asetukset))
          this (assoc this
                 :tila JMS-oliot
                 :yhteys-ok? yhteys-ok?
                 :yhteys-future yhteys-future
                 :kaskytyskanava kaskytyskanava
                 :lopeta-tarkkailu-kanava lopeta-tarkkailu-kanava
                 :saikeen-sammutus-kanava saikeen-sammutus-kanava)
          jms-saije (luo-jms-saije this saikeen-sammutus-kanava)]
      (swap! (:tila this) assoc :jms-saije jms-saije)
      (assoc this
        :yhteyden-tiedot (aloita-sonja-yhteyden-tarkkailu kaskytyskanava lopeta-tarkkailu-kanava (:tyyppi asetukset) db))))

  (stop [{:keys [lopeta-tarkkailu-kanava kaskytyskanava saikeen-sammutus-kanava tila] :as this}]
    #_(when @yhteys-ok?
      (let [tila @tila]
        (some-> tila :yhteys .close)))
    (>!! lopeta-tarkkailu-kanava true)
    ;; Jos on jossain muuaalla jo käsketty sammuuttaa jms-säije, niin tämä jumittaisi. (esim. testeissä)
    (when-not @jms-saije-sammutettu?
      (>!! saikeen-sammutus-kanava true))
    ;; Odotetaan, että käsitteillä olevat viestit on käsitelty
    (<!! (async/go-loop []
           (when (not @jms-saije-sammutettu?)
             (< (timeout 1000))
             (recur))))
    (loop [[jms-saije-sammutettu? _] (async/alts!! [(:jms-saije @tila) (timeout 5000)])
           kierroksia 1]
      (if jms-saije-sammutettu?
        (log/info "Sonja säije sammutettu")
        (do
          (log/info "Ei saatu sammutettua Sonja säijettä " (* kierroksia 5) " sekunnin sisällä.")
          (when (< kierroksia 5)
            (recur (async/alts!! [(:jms-saije @tila) (timeout 5000)])
                   (inc kierroksia))))))
    (assoc this :tila nil
                :yhteys-future nil
                :kaskytyskanava nil
                :lopeta-tarkkailu-kanava nil
                :saikeen-sammutus-kanava nil
                :yhteyden-tiedot nil))

  Sonja
  (kuuntele! [this jonon-nimi kuuntelija-fn jarjestelma]
    (if (some? jonon-nimi)
      (do
        (swap! (:tila this) update-in [:istunnot jarjestelma :jonot jonon-nimi]
               (fn [{kuuntelijat :kuuntelijat}]
                 {:kuuntelijat (conj (or kuuntelijat #{}) kuuntelija-fn)}))
        #(laheta-viesti-kaskytyskanavaan! (:kaskytyskanava this) {:poista-kuuntelija [jarjestelma jonon-nimi kuuntelija-fn]}))
      (do
        (log/warn "jonon nimeä ei annettu, JMS-jonon kuuntelijaa ei käynnistetä")
        (constantly nil))))
  (kuuntele! [this jonon-nimi kuuntelija-fn]
    (kuuntele! this jonon-nimi kuuntelija-fn (str "istunto-" jonon-nimi)))

  (laheta [{kaskytyskanava :kaskytyskanava :as this} jonon-nimi viesti otsikot jarjestelma]
    (let [lahetyksen-viesti (<!! (laheta-viesti-kaskytyskanavaan! kaskytyskanava
                                                                 {:laheta-viesti [jonon-nimi viesti otsikot jarjestelma]}))]
      (cond
        (contains? lahetyksen-viesti :virhe) (throw (:virhe lahetyksen-viesti))
        (contains? lahetyksen-viesti :kaskytysvirhe) (case (:kaskytysvirhe lahetyksen-viesti)
                                                       :jms-saije-sammutettu (throw+ jms-saije-sammutettu-virhe)
                                                       :jms-saijetta-sammutetaan (throw+ jms-saije-sammutetaan-virhe)
                                                       :kasykytyskanava-taynna (throw+ kasykytyskanava-taynna-virhe)
                                                       :aikakatkaisu (throw+ aikakatkaisu-virhe)))
        :else (:vastaus lahetyksen-viesti)))
  (laheta [this jonon-nimi viesti otsikot]
    (laheta this jonon-nimi viesti otsikot (str "istunto-" jonon-nimi)))
  (laheta [this jonon-nimi viesti]
    (laheta this jonon-nimi viesti nil (str "istunto-" jonon-nimi)))

  (aloita-yhteys [{kaskytyskanava :kaskytyskanava :as this}]
    (laheta-viesti-kaskytyskanavaan! kaskytyskanava {:aloita-yhteys nil})))

(defn luo-oikea-sonja [asetukset]
  (->SonjaYhteys asetukset nil nil))

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
      (laheta this jonon-nimi viesti otsikot nil))
    (laheta [this jonon-nimi viesti]
      (laheta this jonon-nimi viesti nil nil))
    (aloita-yhteys [this]
      (log/debug "Feikki Sonja, aloita muka yhteys"))))

(defn luo-sonja [asetukset]
  (if (and asetukset (not (str/blank? (:url asetukset))))
    (luo-oikea-sonja asetukset)
    (luo-feikki-sonja)))
