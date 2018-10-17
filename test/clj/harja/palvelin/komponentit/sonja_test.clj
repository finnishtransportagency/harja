(ns harja.palvelin.komponentit.sonja-test
  (:require [harja.palvelin.asetukset :as asetukset]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.tloik.tyokalut :as tloik-tk]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [clojure.test :refer :all]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.core.async :as a :refer [<!! <! go go-loop thread timeout alts!!]]
            [org.httpkit.client :as http]
            [clojure.xml :as xml]
            [com.stuartsierra.component :as component]

            [harja.palvelin.main :as sut]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sonja-sahkoposti]
            [harja.palvelin.komponentit.tapahtumat :as tapahtumat]))

(defonce asetukset {:sonja {:url "tcp://localhost:61616"
                            :kayttaja ""
                            :salasana ""
                            :tyyppi :activemq}})

(def ^:dynamic *sonja-yhteys* nil)

(defrecord TestiKomponentti [tila]
  component/Lifecycle
  (start [{sonja :sonja :as this}]
    (let [loppu-tila (go (<! (timeout 2000))
                         (if-let [tapahtuma (:tapahtuma @tila)]
                           (:tapahtuma @tila)
                           :ok))]
      (thread
        (case (<!! loppu-tila)
          :ok (swap! tila assoc :testi-jono (sonja/kuuntele! sonja "testi-jono" (fn [])))
          :exception (let [virhe-lahetetty? (atom false)]
                       (with-redefs [sonja/yhdista-kuuntelija (fn [& args]
                                                                (reset! virhe-lahetetty? true)
                                                                (throw Exception))]
                         (sonja/kuuntele! sonja "testi-jono" (fn []))
                         (<!! (go-loop []
                                (when-not @virhe-lahetetty?
                                  (<! (timeout 2000))
                                  (recur))))))))
      this))
  (stop [{sonja :sonja :as this}]
    (when-let [sammutus-fn (-> this :tila deref :testi-jono)]
      (sammutus-fn))))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        #_#_:http-palvelin (testi-http-palvelin)
                        :sonja (sonja/luo-oikea-sonja (:sonja asetukset))
                        :integraatioloki (component/using (integraatioloki/->Integraatioloki nil)
                                                          [:db])
                        :sonja-sahkoposti (component/using
                                            (sonja-sahkoposti/luo-sahkoposti "foo@example.com"
                                                                       {:sahkoposti-sisaan-jono "email-to-harja"
                                                                        :sahkoposti-ulos-jono "harja-to-email"
                                                                        :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                                            [:sonja :db :integraatioloki])
                        :testi-komponentti (component/using
                                             (->TestiKomponentti (atom nil))
                                             [:sonja])
                        #_#_:labyrintti (feikki-labyrintti)
                        :tloik (component/using
                                 (tloik-tk/luo-tloik-komponentti)
                                 [:db :sonja :integraatioloki :klusterin-tapahtumat :sonja-sahkoposti])
                        #_#_:sampo (component/using
                                     (->Sampo +lahetysjono-sisaan+ +kuittausjono-sisaan+ +lahetysjono-ulos+ +kuittausjono-ulos+ nil)
                                     [:db :sonja :integraatioloki])
                        #_#_:db-replica (tietokanta/luo-tietokanta testitietokanta)
                        :klusterin-tapahtumat (component/using
                                                (tapahtumat/luo-tapahtumat)
                                                [:db])
                        #_#_:sonja-jms-yhteysvarmistus (component/using
                                                         (let [{:keys [ajovali-minuutteina jono]} (:sonja-jms-yhteysvarmistus asetukset)]
                                                           (sonja-jms-yhteysvarmistus/->SonjaJmsYhteysvarmistus ajovali-minuutteina jono))
                                                         [:db :pois-kytketyt-ominaisuudet :integraatioloki :sonja :klusterin-tapahtumat])
                        #_#_:sahke (component/using
                                     (sahke/->Sahke +lahetysjono+ nil)
                                     [:db :sonja :integraatioloki])
                        #_#_:status (component/using
                                      (status/luo-status)
                                      [:http-palvelin :db :pois-kytketyt-ominaisuudet :db-replica :sonja])))))
  ;; aloita-sonja palauttaa kanavan.
  (binding [*sonja-yhteys* (sut/aloita-sonja jarjestelma)]
    (testit))
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(defn sonja-laheta [jonon-nimi sanoma]
  (let [options {:timeout 200
                 :basic-auth ["admin" "admin"]
                 :headers {"Content-Type" "application/xml"}
                 :body sanoma}
        {:keys [status error] :as response} @(http/post (str "http://localhost:8161/api/message/" jonon-nimi "?type=queue") options)]))

(defn sonja-laheta-odota [jonon-nimi sanoma]
  (let [kasitellyn-tapahtuman-id (fn []
                                   (not-empty
                                     (first (q (str "SELECT it.id "
                                                    "FROM integraatiotapahtuma it"
                                                    "  JOIN integraatioviesti iv ON iv.integraatiotapahtuma=it.id "
                                                    "WHERE iv.sisalto ILIKE('" (clojure.string/replace sanoma #"ä" "Ã¤") "') AND "
                                                    "it.paattynyt IS NOT NULL")))))]
    (sonja-laheta jonon-nimi sanoma)
    (<!!
      (go-loop [kasitelty? (kasitellyn-tapahtuman-id)
                aika 0]
        (if (or kasitelty? (> aika 10))
          kasitellyn-tapahtuman-id
          (do
            (<! (timeout 1000))
            (recur (kasitellyn-tapahtuman-id)
                   (inc aika))))))))

;;; Sonja komponentin testit
; - Käynnistyy vaikka jokin sonjaa käyttävä komponentti feilaa
;   - Nähdään mitkä komponentit ei ole käynnissä
; - Jos Sonja ei käynnisty, sitä käyttävät komponentit käynnistyy
;   - Nähdään, että Sonja ei ole päällä
; - Jonossa näkyy viestit
; - Viestin käsittelijän lopetus
; - Viestin lähetys onnistuu

;;; Sonjaa käyttävien komponenttien testejä
; - Testataan, että ilmoitukset käsitellään

(deftest sonjan-kaynnistys
  (let [[alkoiko-yhteys? _] (alts!! [*sonja-yhteys* (timeout 10000)])
        {sonja-asetukset :asetukset yhteys-future :yhteys-future yhteys-ok? :yhteys-ok? tila :tila} (:sonja jarjestelma)
        {:keys [yhteys qcf jonot]} @tila]
    (is alkoiko-yhteys? "Yhteys ei alkanut 10 s sisällä")
    (is (= (:sonja asetukset) sonja-asetukset))
    (is @yhteys-future "Yhteyoliota ei luotu")
    (doseq [[jonon-nimi jonon-oliot] jonot]
      (println "Jonon nimi: " jonon-nimi)
      (when-let [olio (:vastaanottaja jonon-oliot)]
        (= (-> olio .getMessageListener meta :kuuntelijoiden-maara) 1))
      (doseq [[avain olio] jonon-oliot]
        (case avain
          ;; :kuuntelijat ei sisällä oliota
          :kuuntelijat (is (every? fn? olio))
          :istunto (do
                     (is (instance? javax.jms.QueueSession olio)))
          :jono (do
                  (is (instance? javax.jms.Queue olio))
                  (is (= (.getQueueName olio) jonon-nimi)))
          :vastaanottaja (do
                           (is (instance? javax.jms.QueueReceiver olio))
                           (is (= (.. olio getQueue getQueueName) jonon-nimi))
                           (is (instance? javax.jms.MessageListener (.getMessageListener olio))))
          :selailija (do
                       (is (instance? javax.jms.QueueBrowser olio))
                       (is (= (.. olio getQueue getQueueName) jonon-nimi)))
          :tuottaja (do
                      (is (instance? javax.jms.MessageProducer olio))
                      (is (= (->> (.getDestination olio) (cast javax.jms.Queue) .getQueueName) jonon-nimi))))))))

(defn tarkista-xml-sisalto
  [{:keys [content tag]} tarkistukset]
  (let [tarkistus-fn (tag tarkistukset)]
    (when tarkistus-fn
      (tarkistus-fn content))
    (if (map? (first content))
      (doseq [content-element content]
        (tarkista-xml-sisalto content-element tarkistukset)))))

(defn purge-jono [istunto jono]
  (let [vastaanottaja (.createReceiver istunto jono)]
    (loop [viesti (.receiveNoWait vastaanottaja)]
      (when viesti
        (recur (.receiveNoWait vastaanottaja))))))

(deftest sonja-yhteys-kaynnistyy-vaikka-sita-kayttava-komponentti-ei
  (swap! (-> jarjestelma :testi-komponentti :tila) assoc :tapahtuma :exception)
  (let [[alkoiko-yhteys? _] (alts!! [*sonja-yhteys* (timeout 10000)])]
    (is alkoiko-yhteys? "Yhteys ei alkanut 10 s sisällä")
    (is (nil? (-> jarjestelma :testi-komponentti :tila deref :testi-jono)))))

(deftest sonja-yhteys-ei-kaynnisty-mutta-sita-kayttavat-komponentit-kylla
  (with-redefs [sonja/aloita-yhdistaminen (fn [& args]
                                            (loop []
                                              (Thread/sleep 1000)
                                              (recur)))]
    ;; Varmistetaan, että component/start ei blokkaa vaikka sonjayhteystä ei saada
    (let [[toinen-jarjestelma _] (alts!! [(thread (component/start
                                                    (component/system-map
                                                      :db (tietokanta/luo-tietokanta testitietokanta)
                                                      #_#_:http-palvelin (testi-http-palvelin)
                                                      :sonja (sonja/luo-oikea-sonja (:sonja asetukset))
                                                      :integraatioloki (component/using (integraatioloki/->Integraatioloki nil)
                                                                                        [:db])
                                                      :sonja-sahkoposti (component/using
                                                                          (sonja-sahkoposti/luo-sahkoposti "foo@example.com"
                                                                                                     {:sahkoposti-sisaan-jono "email-to-harja"
                                                                                                      :sahkoposti-ulos-kuittausjono "harja-to-email-ack"
                                                                                                      :sahkoposti-ja-liite-ulos-kuittausjono "harja-to-email-liite-ack"})
                                                                          [:sonja :db :integraatioloki]))))
                                          (timeout 10000)])
          sonja-yhteys (when toinen-jarjestelma
                         (sut/aloita-sonja toinen-jarjestelma))]
      (is (not (nil? toinen-jarjestelma)) "Järjestelmä ei lähde käyntiin, jos Sonja ei käynnisty")
      ;; Odotellaan vähän aikaa, jotta voidaan varmistua siitä, että :saikeiden-maara lukemisessa ei tule race-conditionia (vaikkei tämän pitäisi olla edes mahdollista, mutta silti).
      ;; Eli tässähän pitäisi käydä niin, että jokaisen sonja/kuuntele! kutsun kohdalla kounteria nostetaan ja sitä lasketaan
      ;; vain, jos kuuntele! nakkaa poikkeuksen tai se suorittaa tehtävänsä loppuun omassa threadissään. Nythän tämä "oma säije" pitäisi blokata sillä, futuren lukeminen
      ;; blokkaa niin kauan, että sen arvo voidaan lukea. Sitä ei tässä testissä voida ikinä lukea, sillä sonja/aloita-yhdistaminen on määritelty ikuiseen
      ;; looppiin. Mutta mikäli "oma säije" pääsisi jotenkin laskemaan counteria, niin silloin syntyisi race-condition.
      (Thread/sleep 2000)
      (is (= 3 (-> toinen-jarjestelma :sonja :tila deref :saikeiden-maara))))))

(deftest viestin-lahetys-onnistuu
  ;; Tässä ei oikeasti lähetä mitään viestiä. Jonoon lähetetään viestiä, mutta sen jonon ei pitäisi olla konffattu lähettämään mitään.
  (let [[alkoiko-yhteys? _] (alts!! [*sonja-yhteys* (timeout 10000)])
        jonot-ennen-lahetysta (-> jarjestelma :sonja :tila deref :jonot)
        _ (sahkoposti/laheta-viesti! (:sonja-sahkoposti jarjestelma) "lahettaja@example.com" "vastaanottaja@example.com" "Testiotsikko" "Testisisalto")
        jonot-lahetyksen-jalkeen (-> jarjestelma :sonja :tila deref :jonot)
        {:keys [jono istunto selailija]} (-> jonot-lahetyksen-jalkeen (get "harja-to-email"))
        viestit-jonossa (.getEnumeration selailija)
        viesti (->> viestit-jonossa .nextElement (cast javax.jms.TextMessage) .getText .getBytes java.io.ByteArrayInputStream. xml/parse)]
    (is (= (count jonot-ennen-lahetysta) (dec (count jonot-lahetyksen-jalkeen))))
    (tarkista-xml-sisalto viesti {:vastaanottajat (fn [vastaanottajat]
                                                    (is (every? #(= :vastaanottaja (:tag %)) vastaanottajat)))
                                  :vastaanottaja (fn [[vastaanottaja]]
                                                   (is (= vastaanottaja "vastaanottaja@example.com")))
                                  :lahettaja (fn [[lahettaja]]
                                               (is (= lahettaja "lahettaja@example.com")))
                                  :otsikko (fn [[otsikko]]
                                             (is (= otsikko "Testiotsikko")))
                                  :sisalto (fn [[sisalto]]
                                             (is (= sisalto "Testisisalto")))})
    (is (not (.hasMoreElements viestit-jonossa)))
    (purge-jono istunto jono)))

#_(deftest main-komponentit-loytyy
  (let [tapahtuma-id (sonja-laheta-odota "tloik-ilmoitusviestijono" (slurp "resources/xsd/tloik/esimerkit/ilmoitus.xml"))]))
