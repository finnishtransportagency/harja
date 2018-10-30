(ns harja.palvelin.komponentit.sonja
  "Komponentti Sonja-väylän JMS-jonoihin liittymiseksi."
  (:require [com.stuartsierra.component :as component]
            [clojure.xml :refer [parse]]
            [clojure.zip :refer [xml-zip]]
            [cheshire.core :as cheshire]
            [clojure.core.async :refer [go-loop go <! >! thread >!! <!! timeout chan dropping-buffer] :as async]
            [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.kyselyt.harjatila :as q]
            [harja.fmt :as fmt])
  (:import (javax.jms Session ExceptionListener JMSException MessageListener)
           (java.lang.reflect Proxy InvocationHandler)
           (java.net InetAddress)
           (java.util Enumeration Collections))
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defonce JMS-alkutila
  {:yhteys nil :istunnot {}})
(defonce jms-kaittelyn-odottelija-numero 0)
(defonce ei-jms-yhteytta {:type :jms-yhteysvirhe
                      :virheet [{:koodi :ei-yhteytta
                                 :viesti "Sonja yhteyttä ei saatu. Viestiä ei voida lähettää."}]})

(defonce aikakatkaisu-virhe {:type :jms-ruuhkaa
                             :virheet [{:koodi :ruuhkaa
                                        :viesti "Sonja-säije ei kyennyt käsittelemään viestiä ajallaan."}]})
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
  `(when ~olio
     (try (. ~olio ~metodi)
          "ACTIVE"
          ~(list 'catch 'javax.jms.IllegalStateException 'e
                 "CLOSED"))))

(defn hae-jonon-viestit [istunto jono]
  "Luodaan selailija olio aina uudestaan, koska se saa jonosta snapshot tilanteen silloin, kun se luodaan."
  (when (and istunto jono)
    (let [selailija (.createBrowser istunto jono)
          viesti-elementit (.getEnumeration selailija)]
      (try (loop [elementit []]
             ;; Selailija olio ei ainakaan ActiveMQ:n kanssa toimi oikein kun istunnossa on useita eri jonoja. hasMoreElements
             ;; palauttaa oikein true sillin, kun siellä on viestejä, mutta nextElement palauttaa siitä huolimatta nil
             (if (.hasMoreElements viesti-elementit)
               (let [elementti (->> viesti-elementit .nextElement)]
                 (if (nil? elementti)
                   (conj elementit nil)
                   (recur (conj elementit elementti))))
               elementit))
           (catch JMSException e
             nil)
           (finally
             (when selailija
               (.close selailija)))))))

(declare tee-jms-poikkeuskuuntelija
         laheta-viesti-kaskytyskanavaan)

(defn aloita-sonja-yhteyden-tarkkailu [kaskytys-kanava lopeta-tarkkailu-kanava tyyppi db]
  (go-loop [[lopetetaan? _] (async/alts! [lopeta-tarkkailu-kanava]
                                         :default false)]
    (when-not lopetetaan?
      (let [vastaus (laheta-viesti-kaskytyskanavaan kaskytys-kanava {:jms-tilanne [tyyppi db]})]
        (<! (timeout 5000))
        (recur (async/alts! [lopeta-tarkkailu-kanava]
                            :default false))))))

(defn- poista-consumerit [{jonot :jonot :as tila}]
  (reduce (fn [tila jono]
            (assoc-in tila [:jonot jono :consumer] nil))
          tila
          (keys jonot)))

(defn yhdista-uudelleen [{:keys [tila yhteys-ok? kaskytys-kanava] :as sonja}]
    (let [katkos-alkoi (pvm/nyt-suomessa)
          nukkumisaika 5000
          {yhteys :yhteys} @tila]
      (log/info "Yritetään yhdistään JMS-yhteys uudelleen. Katkos alkoi: " (str katkos-alkoi))
      (when yhteys
        (try
          (.close yhteys)
          (reset! yhteys-ok? false)
          (catch Exception e
            (log/error e "JMS-yhteyden sulkemisessa tapahtui poikkeus: " (.getMessage e)))))
      (thread
        (doto (Thread/currentThread)
          (.setName "jms-reconnecting-saija"))
        (<!! (laheta-viesti-kaskytyskanavaan kaskytys-kanava {:yhdista-uudelleen [katkos-alkoi]}))
        (let [kulunut-aika (pvm/aikavali-sekuntteina katkos-alkoi (pvm/nyt-suomessa))]
          (log/info "Sonja jonot pystytetty uudestaan. Aikaa meni: " kulunut-aika)))))

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
    (catch Exception e
      (log/error "JMS brokeriin yhdistäminen epäonnistui: " e)
      nil)))

(defn aloita-yhdistaminen [{:keys [url tyyppi] :as asetukset}]
  (log/info "Yhdistetään " (if (= tyyppi :activemq) "ActiveMQ" "Sonic") " JMS-brokeriin URL:lla:" url)
  (let [qcf (luo-connection-factory url tyyppi)
        yhteys (yhdista asetukset qcf 10000)]
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

(defmulti jms-toiminto
  (fn [sonja kasky]
    (-> kasky keys first)))

(defmethod jms-toiminto :aloita-yhteys
  [{:keys [tila yhteys-ok?] :as sonja} kasky]
  (let [params (when-let [params (vals kasky)]
                 (first params))
        {:keys [istunnot yhteys]} @tila
        poikkeuskuuntelija (tee-jms-poikkeuskuuntelija sonja)]
    ;; Alustetaan vastaanottaja jvm oliot
    (doseq [[jarjestelma {jonot :jonot}] istunnot]
      (doseq [[jonon-nimi _] jonot]
        (varmista-jms-objektit tila jonon-nimi jarjestelma :vastaanottaja)))
    ;; Lisätään poikkeuskuuntelija yhteysolioon
    (.setExceptionListener yhteys poikkeuskuuntelija)
    ;; Aloita yhteys
    (.start yhteys)
    (reset! yhteys-ok? true)
    true))

(defmethod jms-toiminto :yhdista-uudelleen
  [{:keys [asetukset tila kaskytys-kanava] :as sonja} kasky]
  (let [params (when-let [params (vals kasky)]
                 (first params))
        [katkos-alkoi] params
        yhteys-future (aloita-yhdistaminen asetukset)
        yhteys-olio @yhteys-future]
    (swap! tila merge yhteys-olio)
    (swap! tila update :istunnot (fn [istunnot]
                                   (reduce (fn [tulos [istunnon-nimi m]]
                                             (let [m (select-keys m [:jonot])
                                                   jonot (:jonot m)]
                                               (merge tulos
                                                      {istunnon-nimi
                                                       {:jonot (into {}
                                                                     (map (fn [[jonon-nimi jms-oliot]]
                                                                            [jonon-nimi (select-keys jms-oliot [:kuuntelijat])])
                                                                          jonot))}})))
                                           {} istunnot)))
    (jms-toiminto sonja {:aloita-yhteys nil})))

(defmethod jms-toiminto :laheta-viesti
  [{:keys [tila yhteys-ok? kaskytys-kanava]} kasky]
  (let [params (when-let [params (vals kasky)]
                 (first params))
        [jonon-nimi viesti {:keys [correlation-id]} jarjestelma] params]
    (varmista-jms-objektit tila jonon-nimi jarjestelma :tuottaja)
    ;; Meidän ei tarvitse olla varmoja, että yhteys on aloitettu (eli start metodi on kutsuttu) silloin kun
    ;; lähetetään viestejä. JMS jonoissa se ei ole pakollista.
    (laheta-viesti tila jonon-nimi viesti correlation-id jarjestelma)))

(defmethod jms-toiminto :poista-kuuntelija
  [{:keys [tila]} kasky]
  (let [params (when-let [params (vals kasky)]
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
        (kasittele-viesti vastaanottaja kuuntelijat tila jarjestelma jonon-nimi)
        (swap! tila update-in [:istunnot jarjestelma :jonot jonon-nimi]
               (fn [jonon-tiedot]
                 (assoc jonon-tiedot :vastaanottaja vastaanottaja
                                     :kuuntelijat kuuntelijat)))))
    true))

(defmethod jms-toiminto :jms-tilanne
  [{:keys [tila yhteys-ok? kaskytys-kanava]} kasky]
  (let [params (when-let [params (vals kasky)]
                 (first params))
        [tyyppi db] params
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
                                             :jarjestelma jarjestelma
                                             :jonot (mapv (fn [[jonon-nimi {:keys [jono tuottaja vastaanottaja virheet]}]]
                                                            (let [jonon-viestit (map #(identity
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
                                    (filter #(if (re-find #"^jms-(saije|kasittelyn-odottelija)" (.getName %))
                                               %))
                                    (map #(identity
                                            {:nimi (.getName %)
                                             :status (.. % getState toString)})))
                                  (.keySet (Thread/getAllStackTraces)))]
    (q/tallenna-sonjan-tila<! db {:tila (cheshire/encode {:olioiden-tilat olioiden-tilat
                                                          :saikeiden-tilat saikeiden-tilat})
                                  :palvelin (fmt/leikkaa-merkkijono 512
                                                                    (.toString (InetAddress/getLocalHost)))
                                  :osa-alue "sonja"})))

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
  [{:keys [yhteys-future tila kaskytys-kanava] :as sonja} sammutus-kanava]
  (thread
    (doto (Thread/currentThread)
      (.setName "jms-saije"))
    ;; Ensin varmistetaan, että yhteys sonjaan on saatu. futuren dereffaaminen blokkaa, kunnes saadaan
    ;; joku arvo pihalle.
    (let [yhteys-olio @yhteys-future]
      (swap! tila merge yhteys-olio)
      ;; Aloitetaan ikuinen looppi
      (loop [;; Tarkistetaan joka kieroksella, että onko tullut käsky tuhota tämä säije
             [lopetetaan? _] (async/alts!! [sammutus-kanava]
                                           :default false)]
        (let [[{:keys [kasky kaskytys-kanavan-vastaus]} _] (if lopetetaan?
                                                             ;; Lopetuksen jälkeen käsitellään ensin kaikki kanavassa jo olevat
                                                             ;; käskyt
                                                             (async/alts!! [kaskytys-kanava]
                                                                           :default {:kasky :saije-lopetetaan})
                                                             ;; Jos ei niin jäädään odottelemaan seuraavaa käskyä. Kummiskin loopataan
                                                             ;; vähintään 3 sek välein, jotta voidaan tarkastaa, että onko säije
                                                             ;; tarkoitus lopettaa
                                                             (async/alts!! [kaskytys-kanava (timeout 3000)]))]
          (if-not (or (= kasky :saije-lopetetaan)
                      ;; Tässä tapauksessa kaskytys-kanava on closetettu
                      (and lopetetaan? (nil? kasky)))
            (do
              ;; Saatiinko käsky vai kerkesikö timeout mennä loppuun?
              (when-not (nil? kasky)
                (>!! kaskytys-kanavan-vastaus :valmis-kasiteltavaksi)
                (when (= :kasittele (<!! kaskytys-kanavan-vastaus))
                  (>!! kaskytys-kanavan-vastaus
                       (try (jms-toiminto sonja kasky)
                            (catch Throwable t
                              (log/error "Jokin meni vikaan sonjakäskyissä: " t)
                              {:virhe t})))))
              (recur (if lopetetaan?
                       [true nil]
                       (async/alts!! [sammutus-kanava]
                                     :default false))))
            (do
              (async/close! kaskytys-kanava)
              (>!! sammutus-kanava true))))))))

(defn laheta-viesti-kaskytyskanavaan
  "Lähettää käskyn jms-saikeelle. Käskytyskanavalle on määritelty dropping-buffer, joten se pudottaa lähetetyn viestin
   mikäli se on täynnä. Tämän takia annetaan timeout arvo, jotta voidaan ilmoittaa viestin lähettäjää, että nyt ei onnistunut
   viestin käsittely toivotussa ajassa. Timeoutin lisääminen aiheuttaa siinä mielessä harmia, että jms-saikeen täytyy
   kysyä tältä säikeeltä, että onko timeout kerennyt jo tapahuta siinä vaiheessa, kun jms-saie olisi valmis käsittelemään
   tämän viestin.
   Palauttaa kanavan, josta tuloksen voi lukea."
  [kaskytys-kanava kasky]
  (let [kaskytys-kanavan-vastaus (chan)
        _ (>!! kaskytys-kanava {:kasky kasky :kaskytys-kanavan-vastaus kaskytys-kanavan-vastaus})
        [tulos _] (async/alts!! [(timeout 5000) kaskytys-kanavan-vastaus])]
    (if (= tulos :valmis-kasiteltavaksi)
      (do (>!! kaskytys-kanavan-vastaus :kasittele)
          (thread
            (doto (Thread/currentThread)
              (.setName (str "jms-kasittelyn-odottelija*" (-> kasky keys first name) "*" (inc jms-kaittelyn-odottelija-numero))))
            (<!! kaskytys-kanavan-vastaus)))
      (do (>!! kaskytys-kanavan-vastaus :aika-katkaisu)
          (let [vastaus-kanava (chan)]
            (>!! vastaus-kanava {:virhe "Aikakatkaistiin"})
            vastaus-kanava)))))

(defrecord SonjaYhteys [asetukset tila yhteys-ok?]
  component/Lifecycle
  (start [{db :db
           :as this}]
    (let [JMS-oliot (atom JMS-alkutila)
          yhteys-ok? (atom false)
          kaskytys-kanava (chan (dropping-buffer 100))
          lopeta-tarkkailu-kanava (chan)
          saikeen-sammutus-kanava (chan)
          yhteys-future (future
                          (aloita-yhdistaminen asetukset))
          this (assoc this
                 :tila JMS-oliot
                 :yhteys-ok? yhteys-ok?
                 :yhteys-future yhteys-future
                 :kaskytys-kanava kaskytys-kanava
                 :lopeta-tarkkailu-kanava lopeta-tarkkailu-kanava
                 :saikeen-sammutus-kanava saikeen-sammutus-kanava)
          jms-saije (luo-jms-saije this saikeen-sammutus-kanava)]
      (swap! (:tila this) assoc :jms-saije jms-saije)
      (assoc this
        :yhteyden-tiedot (aloita-sonja-yhteyden-tarkkailu kaskytys-kanava lopeta-tarkkailu-kanava (:tyyppi asetukset) db))))

  (stop [{:keys [lopeta-tarkkailu-kanava kaskytys-kanava saikeen-sammutus-kanava] :as this}]
    (when @yhteys-ok?
      (let [tila @tila]
        (some-> tila :yhteys .close)))
    (>!! lopeta-tarkkailu-kanava true)
    (async/close! kaskytys-kanava)
    (>!! saikeen-sammutus-kanava true)
    (loop [[sammutettu? _] (async/alts!! [saikeen-sammutus-kanava (timeout 5000)])
           kierroksia 1]
      (if sammutettu?
        (log/info "Sonja säije sammutettu")
        (do
          (log/info "Ei saatu sammutettua Sonja säijettä " (* kierroksia 5) " sekunnin sisällä.")
          (when (< kierroksia 5)
            (recur (async/alts!! [saikeen-sammutus-kanava (timeout 5000)])
                   (inc kierroksia))))))
    (assoc this :tila nil
                :yhteys-future nil
                :kaskytys-kanava nil
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
        #(laheta-viesti-kaskytyskanavaan (:kaskytys-kanava this) {:poista-kuuntelija [jarjestelma jonon-nimi kuuntelija-fn]}))
      (do
        (log/warn "jonon nimeä ei annettu, JMS-jonon kuuntelijaa ei käynnistetä")
        (constantly nil))))
  (kuuntele! [this jonon-nimi kuuntelija-fn]
    (kuuntele! this jonon-nimi kuuntelija-fn (str "istunto-" jonon-nimi)))

  (laheta [{kaskytys-kanava :kaskytys-kanava :as this} jonon-nimi viesti otsikot jarjestelma]
    (let [lahetyksen-viesti (<!! (laheta-viesti-kaskytyskanavaan kaskytys-kanava
                                                                 {:laheta-viesti [jonon-nimi viesti otsikot jarjestelma]}))]
      (if (and (map? lahetyksen-viesti) (contains? lahetyksen-viesti :virhe))
        (if (= "Aikakatkaistiin" (:virhe lahetyksen-viesti))
          (throw+ aikakatkaisu-virhe)
          (throw (:virhe lahetyksen-viesti)))
        lahetyksen-viesti)))
  (laheta [this jonon-nimi viesti otsikot]
    (laheta this jonon-nimi viesti otsikot (str "istunto-" jonon-nimi)))
  (laheta [this jonon-nimi viesti]
    (laheta this jonon-nimi viesti nil (str "istunto-" jonon-nimi)))

  (aloita-yhteys [{kaskytys-kanava :kaskytys-kanava :as this}]
    (laheta-viesti-kaskytyskanavaan kaskytys-kanava {:aloita-yhteys nil})))

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
