(ns harja.palvelin.komponentit.sonja-test
  (:require [harja.palvelin.asetukset :as asetukset]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.tloik.tyokalut :as tloik-tk]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [clojure.test :refer :all]
            [clojure.string :as str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.core.async :as a :refer [<!! <! >!! >! go go-loop thread timeout alts!! chan]]
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

(defrecord Testikomponentti [tila]
  component/Lifecycle
  (start [{sonja :sonja :as this}]
    (let [tila (atom nil)
          ;; Tässä go-blockissa oletetaan, että kahden sekunnin aikana jarjestelma keretään alustaa ja päästä testin sisälle, jossa testin alussa
          ;; voidaan halutessa muuttaa 'tila' atomin arvoa haluttuun arvoon.
          lopputila (go (<! (timeout 2000))
                        (if-let [tapahtuma (:tapahtuma @tila)]
                          (:tapahtuma @tila)
                          :ok))
          testijonokanava (chan)
          testijonot (chan)]
      (thread
        (case (<!! lopputila)
          :ok (>!! testijonot {:testijono (sonja/kuuntele! sonja "testijono" (fn []))})
          :exception (>!! testijonot {:testijono (sonja/kuuntele! sonja "testijono" (fn []
                                                                                      (throw (Exception. "VIRHE"))))})
          :useampi-kuuntelija (>!! testijonot
                                   {:testijono-1 (sonja/kuuntele! sonja "testijono" ^{:judu :testijono-1} (fn [_]
                                                                                                            (>!! testijonokanava :viestia-kasitellaan)
                                                                                                            ;; Nukutaan sekuntti, jotta 'pääsäikeessä' keretään sammuttaa kuuntelija.
                                                                                                            (Thread/sleep 1000)
                                                                                                            (swap! tila update :testikasittelyita (fn [laskuri]
                                                                                                                                                    (if laskuri (inc laskuri) 1)))))
                                    :testijono-2 (sonja/kuuntele! sonja "testijono" ^{:judu :testijono-2} (fn [_]
                                                                                                            (swap! tila update :testikasittelyita (fn [laskuri]
                                                                                                                                                    (if laskuri (inc laskuri) 1)))))})))
      (assoc this :testijonot testijonot
                  :testijonokanava testijonokanava
                  :tila tila)))
  (stop [{sonja :sonja :as this}]
    (doseq [jono [:testijono :testijono-1 :testijono-2]]
      (when-let [sammutus-fn (-> this :tila deref jono)]
        (sammutus-fn)))))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db ds
                        #_#_:http-palvelin (testi-http-palvelin)
                        :sonja (component/using
                                 (sonja/luo-oikea-sonja (:sonja asetukset))
                                 [:db])
                        :integraatioloki (component/using (integraatioloki/->Integraatioloki nil)
                                                          [:db])
                        :sonja-sahkoposti (component/using
                                            (sonja-sahkoposti/luo-sahkoposti "foo@example.com"
                                                                             {:sahkoposti-sisaan-jono "email-to-harja"
                                                                              :sahkoposti-ulos-jono "harja-to-email"
                                                                              :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                                            [:sonja :db :integraatioloki])
                        :testikomponentti (component/using
                                            (->Testikomponentti nil)
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
  (binding [*sonja-yhteys* (go
                             ;; Ennen kuin aloitetaan yhteys, varmistetaan, että testikomponentin thread on päässyt loppuun
                             (let [testijonot (<! (-> jarjestelma :testikomponentti :testijonot))]
                               (swap! (-> jarjestelma :testikomponentti :tila) merge testijonot))
                             (<! (sut/aloita-sonja jarjestelma)))]
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
;   - Nähdään mitkä komponentit ei ole käynnissä
;   - Nähdään, että Sonja ei ole päällä
; - Jonossa näkyy viestit

;;; Sonjaa käyttävien komponenttien testejä
; - Testataan, että ilmoitukset käsitellään

(deftest sonjan-kaynnistys
  (let [[alkoiko-yhteys? _] (alts!! [*sonja-yhteys* (timeout 10000)])
        {sonja-asetukset :asetukset yhteys-future :yhteys-future yhteys-ok? :yhteys-ok? tila :tila
         db :db kaskytys-kanava :kaskytys-kanava yhteyden-tiedot :yhteyden-tiedot} (:sonja jarjestelma)
        {:keys [yhteys qcf istunnot jms-saije]} @tila]
    (is alkoiko-yhteys? "Yhteys ei alkanut 10 s sisällä")
    (is (= (:sonja asetukset) sonja-asetukset))
    (is @yhteys-future "Yhteyoliota ei luotu")
    (doseq [[istunnon-nimi istunnon-oliot] istunnot]
      (let [{:keys [jonot istunto]} istunnon-oliot
            [jonon-nimi jonon-oliot] (first jonot)]
        (is (instance? javax.jms.QueueSession istunto))
        (is (= 1 (count jonot)))
        (doseq [[avain olio] jonon-oliot]
          (case avain
            ;; :kuuntelijat ei sisällä oliota
            :kuuntelijat (is (every? fn? olio))
            :jono (do
                    (is (instance? javax.jms.Queue olio))
                    (is (= (.getQueueName olio) jonon-nimi)))
            :vastaanottaja (do
                             (is (instance? javax.jms.QueueReceiver olio))
                             (is (= (.. olio getQueue getQueueName) jonon-nimi))
                             (is (instance? javax.jms.MessageListener (.getMessageListener olio)))
                             (is (= (-> olio .getMessageListener meta :kuuntelijoiden-maara) 1)))
            :tuottaja (do
                        (is (instance? javax.jms.MessageProducer olio))
                        (is (= (->> (.getDestination olio) (cast javax.jms.Queue) .getQueueName) jonon-nimi)))))))))

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

#_(deftest virhe-kasittelija-funktiossa
    (swap! (-> jarjestelma :testikomponentti :tila) assoc :tapahtuma :exception)
    (let [[alkoiko-yhteys? _] (alts!! [*sonja-yhteys* (timeout 10000)])]
      (is alkoiko-yhteys? "Yhteys ei alkanut 10 s sisällä")
      (is (nil? (-> jarjestelma :testikomponentti :tila deref :testijono)))))

(deftest sonja-yhteys-ei-kaynnisty-mutta-sita-kayttavat-komponentit-kylla
  ;; Odotetaan, että oletusjärjestelmä on pystyssä. Tässä testissä siitä ei olla kiinostuneita.
  (<!! *sonja-yhteys*)
  (with-redefs [sonja/aloita-yhdistaminen (fn [& args]
                                            (loop []
                                              (Thread/sleep 1000)
                                              (recur)))]
    ;; Varmistetaan, että component/start ei blokkaa vaikka sonjayhteystä ei saada
    (let [[toinen-jarjestelma _] (alts!! [(thread (component/start
                                                    (component/system-map
                                                      :db (tietokanta/luo-tietokanta testitietokanta)
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
      (is (nil? (first (alts!! [sonja-yhteys (timeout 1000)]))) "Sonja yhteyden aloittaminen ei blokkaa vaikka yhteys ei ole käytössä"))))

(deftest lopeta-kuuntelija
  (swap! (-> jarjestelma :testikomponentti :tila) assoc :tapahtuma :useampi-kuuntelija)
  (let [_ (alts!! [*sonja-yhteys* (timeout 10000)])
        testikomponentti (:testikomponentti jarjestelma)
        testijonokanava (:testijonokanava testikomponentti)
        {:keys [istunto jono vastaanottaja]} (-> jarjestelma :sonja :tila deref :jonot (get "testijono"))
        tuottaja (.createProducer istunto jono)
        msg (sonja/luo-viesti "foo" istunto)]
    (is (= 2 (-> vastaanottaja .getMessageListener meta :kuuntelijoiden-maara)))
    ;; Lähetetään viesti
    (.send tuottaja msg)
    ;; Odotetaan, että viestiä käsitellään
    (<!! testijonokanava)
    ;; lopetetaan yksi kuuntelija
    ((-> testikomponentti :tila deref :testijono-1))
    ;; Lopetuksen pitäisi blokata ja odotella, että consumer saa hoidettua hommansa
    (is (= (-> testikomponentti :tila deref :testikasittelyita) 2))))

(deftest viestin-lahetys-onnistuu
  ;; Tässä ei oikeasti lähetä mitään viestiä. Jonoon lähetetään viestiä, mutta sen jonon ei pitäisi olla konffattu lähettämään mitään.
  (let [_ (alts!! [*sonja-yhteys* (timeout 10000)])
        jonot-ennen-lahetysta (-> jarjestelma :sonja :tila deref :jonot)
        _ (sahkoposti/laheta-viesti! (:sonja-sahkoposti jarjestelma) "lahettaja@example.com" "vastaanottaja@example.com" "Testiotsikko" "Testisisalto")
        jonot-lahetyksen-jalkeen (-> jarjestelma :sonja :tila deref :jonot)
        {:keys [jono istunto]} (-> jonot-lahetyksen-jalkeen (get "harja-to-email"))
        viestit-jonossa (sonja/hae-jonon-viestit istunto jono)
        viesti (->> viestit-jonossa first (cast javax.jms.TextMessage) .getText .getBytes java.io.ByteArrayInputStream. xml/parse)]
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
    (is (= 1 (count viestit-jonossa)))
    (purge-jono istunto jono)))



#_(deftest main-komponentit-loytyy
    (let [tapahtuma-id (sonja-laheta-odota "tloik-ilmoitusviestijono" (slurp "resources/xsd/tloik/esimerkit/ilmoitus.xml"))]))
