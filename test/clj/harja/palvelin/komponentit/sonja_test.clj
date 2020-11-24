(ns ^:integraatio harja.palvelin.komponentit.sonja-test
  (:require [harja.palvelin.asetukset :as asetukset]
            [harja.testi :refer :all]
            [harja.palvelin.integraatiot.tloik.tyokalut :as tloik-tk]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [harja.kyselyt.konversio :as konv]
            [clojure.test :refer :all]
            [clojure.string :as clj-str]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.core.async :as a :refer [<!! <! >!! >! go go-loop thread timeout put! alts!! chan poll!]]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [org.httpkit.client :as http]
            [clojure.xml :as xml]
            [com.stuartsierra.component :as component]
            [cheshire.core :as cheshire]
            [slingshot.slingshot :refer [try+]]

            [harja.palvelin.main :as sut]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sonja-sahkoposti]
            [harja.palvelin.integraatiot.sonja.tyokalut :as s-tk]))

(defonce asetukset {:sonja {:url "tcp://localhost:61617"
                            :kayttaja ""
                            :salasana ""
                            :tyyppi :activemq}
                    :tloik {:ilmoitusviestijono tloik-tk/+tloik-ilmoitusviestijono+
                            :ilmoituskuittausjono tloik-tk/+tloik-ilmoituskuittausjono+
                            :toimenpideviestijono tloik-tk/+tloik-ilmoitustoimenpideviestijono+
                            :toimenpidekuittausjono tloik-tk/+tloik-ilmoitustoimenpidekuittausjono+
                            :uudelleenlahetysvali-minuuteissa 30}})

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
          lahetys-fn (fn [viesti]
                       (let [vastaus (sonja/laheta sonja "testilahetys-jono" viesti nil "testijarjestelma-lahetys")]
                         vastaus))
          testijonokanava (chan 1000)
          testijonot (chan)]
      (thread
        (>!! testijonot
             (case (<!! lopputila)
               :ok {:testijono (sonja/kuuntele! sonja "testijono" (fn [_]) "testijarjestelma")}
               :exception {:testijono (sonja/kuuntele! sonja "virhetestijono" (fn [viesti]
                                                                           (throw (Exception. "VIRHE")))
                                                       "testijarjestelma")}
               :useampi-kuuntelija {:testijono-1 (sonja/kuuntele! sonja "testijono" ^{:judu :testijono-1} (fn [_]
                                                                                                            (>!! testijonokanava :viestia-kasitellaan)
                                                                                                            ;; Nukutaan sekuntti, jotta 'pääsäikeessä' keretään sammuttaa kuuntelija.
                                                                                                            (<!! (timeout 1000))
                                                                                                            (swap! tila update :testikasittelyita (fn [laskuri]
                                                                                                                                                    (if laskuri (inc laskuri) 1))))
                                                                  "testijarjestelma")
                                    :testijono-2 (sonja/kuuntele! sonja "testijono" ^{:judu :testijono-2} (fn [_]
                                                                                                            (swap! tila update :testikasittelyita (fn [laskuri]
                                                                                                                                                    (if laskuri (inc laskuri) 1))))
                                                                  "testijarjestelma")}
               :kuormitus {:testijono-1 (sonja/kuuntele! sonja "testijono-1" (fn [viesti]
                                                                               (Thread/sleep (.intValue (* 100 (rand))))
                                                                               (throw (Exception.))
                                                                               (put! testijonokanava viesti)))
                           :testijono-2 (sonja/kuuntele! sonja "testijono-2" (fn [viesti]
                                                                               (Thread/sleep (.intValue (* 100 (rand))))
                                                                               (put! testijonokanava viesti)))})))
      (assoc this :testijonot testijonot
                  :testijonokanava testijonokanava
                  :lahetys-fn lahetys-fn
                  :tila tila)))
  (stop [{sonja :sonja :as this}]
    (doseq [jono [:testijono :testijono-1 :testijono-2]]
      (when-let [sammutus-fn (-> this :tila deref jono)]
        (sammutus-fn)))
    (assoc this :testijonot nil
                :testijonokanava nil
                :lahetys-fn nil
                :tila nil)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db ds
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
                        :tloik (component/using
                                 (tloik-tk/luo-tloik-komponentti)
                                 [:db :sonja :integraatioloki :sonja-sahkoposti])))))
  ;; aloita-sonja palauttaa kanavan.
  (binding [*sonja-yhteys* (go
                             ;; Ennen kuin aloitetaan yhteys, varmistetaan, että testikomponentin thread on päässyt loppuun
                             (let [testijonot (<! (-> jarjestelma :testikomponentti :testijonot))]
                               (swap! (-> jarjestelma :testikomponentti :tila) merge testijonot))
                             (<! (sut/aloita-sonja jarjestelma)))]
    (testit))
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest sonjan-kaynnistys
  (let [[alkoiko-yhteys? _] (alts!! [*sonja-yhteys* (timeout 10000)])
        {sonja-asetukset :asetukset yhteys-future :yhteys-future yhteys-ok? :yhteys-ok? tila :tila
         db :db kaskytyskanava :kaskytyskanava} (:sonja jarjestelma)
        {:keys [yhteys qcf istunnot jms-saije]} @tila]
    (is alkoiko-yhteys? "Yhteys ei alkanut 10 s sisällä")
    (is (= (:sonja asetukset) sonja-asetukset))
    (is (realized? yhteys-future) "Yhteyoliota ei luotu")
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

(deftest virhe-kasittelija-funktiossa
    (swap! (-> jarjestelma :testikomponentti :tila) assoc :tapahtuma :exception)
    (is (first (alts!! [*sonja-yhteys* (timeout 10000)])) "Yhteys ei alkanut 10 s sisällä")
    (is (not (nil? (-> jarjestelma :testikomponentti :tila deref :testijono))))
    (s-tk/sonja-laheta "virhetestijono" "foo")
    ;; Odotetaan, että viesti on käsitelty
    (loop [n 0
           kasitelty? false]
      (println "Looppeja: " n)
      (when (and (< n 3)
                 (not kasitelty?))
        (recur (inc n)
               (= 0
                  (- (-> (s-tk/sonja-jolokia-jono "virhetestijono" :enqueue-count nil) :body (cheshire/decode) (get "value"))
                     (-> (s-tk/sonja-jolokia-jono "virhetestijono" :dequeue-count nil) :body (cheshire/decode) (get "value")))))))
    (is (= "VIRHE" (-> jarjestelma :sonja :tila deref :istunnot (get "testijarjestelma") :jonot (get "virhetestijono") :virheet first :viesti)))
    (s-tk/sonja-jolokia-jono "virhetestijono" nil :purge))

(deftest sonja-yhteys-ei-kaynnisty-mutta-sita-kayttavat-komponentit-kylla
  ;; Odotetaan, että oletusjärjestelmä on pystyssä. Tässä testissä siitä ei olla kiinostuneita.
  (<!! *sonja-yhteys*)
  (let [sulje-sonja-kanava (chan)]
    (with-redefs [sonja/aloita-yhdistaminen (fn [& args]
                                              (loop []
                                                (if (not (poll! sulje-sonja-kanava))
                                                  (do
                                                    (Thread/sleep 1000)
                                                    (recur))
                                                  {})))]
      ;; Varmistetaan, että component/start ei blokkaa vaikka sonjayhteyttä ei saada
      (let [[toinen-jarjestelma _] (alts!! [(thread (component/start
                                                      (component/system-map
                                                        :db (tietokanta/luo-tietokanta testitietokanta)
                                                        :sonja (component/using
                                                                 (sonja/luo-oikea-sonja (:sonja asetukset))
                                                                 [:db])
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
        (is (nil? (first (alts!! [sonja-yhteys (timeout 1000)]))) "Sonja yhteyden aloittaminen blokkaa vaikka yhteys ei ole käytössä")
        (>!! sulje-sonja-kanava true)
        (-> toinen-jarjestelma :sonja component/stop)))))

(deftest lopeta-kuuntelija
  (swap! (-> jarjestelma :testikomponentti :tila) assoc :tapahtuma :useampi-kuuntelija)
  (let [_ (alts!! [*sonja-yhteys* (timeout 10000)])
        testikomponentti (:testikomponentti jarjestelma)
        testijonokanava (:testijonokanava testikomponentti)
        {:keys [istunto jonot]} (-> jarjestelma :sonja :tila deref :istunnot (get "testijarjestelma"))
        {:keys [jono vastaanottaja]} (get jonot "testijono")]
    ;; Lähetetään viesti
    (sonja/laheta (-> jarjestelma :sonja) "testijono" "foo" nil "testijarjestelma")
    (is (= 2 (-> vastaanottaja .getMessageListener meta :kuuntelijoiden-maara)))
    ;; Odotetaan, että viestiä käsitellään
    (<!! testijonokanava)
    ;; lopetetaan yksi kuuntelija
    (let [kanava ((-> testikomponentti :tila deref :testijono-1))
          ;; Odotetaan, että :poista-kuuntelija multimetodi on ajettu loppuun
          [tulos _] (alts!! [kanava (timeout 10000)])
          {:keys [vastaanottaja]} (-> jarjestelma :sonja :tila deref :istunnot (get "testijarjestelma") :jonot (get "testijono"))]
      ;; Lopetuksen pitäisi blokata ja odotella, että consumer saa hoidettua hommansa
      (is (= (-> testikomponentti :tila deref :testikasittelyita) 2))
      (is tulos "Kuuntelijan poisto ei onnistunut")
      ;; Tarkistetaan, että onhan kuuntelijoita enää yksi jäljellä
      (is (= 1 (-> vastaanottaja .getMessageListener meta :kuuntelijoiden-maara))))))

(deftest viestin-lahetys-onnistuu
  ;; Tässä ei oikeasti lähetä mitään viestiä. Jonoon lähetetään viestiä, mutta sen jonon ei pitäisi olla konffattu lähettämään mitään.
  (let [_ (alts!! [*sonja-yhteys* (timeout 10000)])
        _ (s-tk/sonja-jolokia-jono "harja-to-email" nil :purge)
        istunnot-ennen-lahetysta (-> jarjestelma :sonja :tila deref :istunnot)
        jonot-ennen-lahetysta (apply merge
                                     (map (fn [[istunnon-nimi istunnon-tiedot]]
                                            (:jonot istunnon-tiedot))
                                          istunnot-ennen-lahetysta))
        _ (sahkoposti/laheta-viesti! (:sonja-sahkoposti jarjestelma) "lahettaja@example.com" "vastaanottaja@example.com" "Testiotsikko" "Testisisalto")
        istunnot-lahetyksen-jalkeen (-> jarjestelma :sonja :tila deref :istunnot)
        jonot-lahetyksen-jalkeen (apply merge
                                        (map (fn [[istunnon-nimi istunnon-tiedot]]
                                               (:jonot istunnon-tiedot))
                                             istunnot-lahetyksen-jalkeen))
        istunto (-> istunnot-lahetyksen-jalkeen (get "istunto-harja-to-email") :istunto)
        jono (-> jonot-lahetyksen-jalkeen (get "harja-to-email") :jono)
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
    (is (= 1 (count viestit-jonossa)))))

(s/def ::testilahetys-viesti string?)

(defn suorita-rinnakkain [f s]
  (doall (for [m (if (number? s)
                   (range s)
                   s)]
           (thread (try+ (f m)
                        (catch [:type :jms-kaskytysvirhe] {:keys [virheet]}
                          (let [virhe (first virheet)]
                            (:koodi virhe)))
                        (catch Throwable t
                          (println (str "VIRHE: " (.getMessage t) " " (.getStackTrace t)))
                          "VIRHE"))))))

(deftest sonja-kuormitus-testi
  (swap! (-> jarjestelma :testikomponentti :tila) assoc :tapahtuma :kuormitus)
  (let [_ (alts!! [*sonja-yhteys* (timeout 10000)])
        _ (s-tk/sonja-jolokia-jono "testilahetys-jono" nil :purge)
        _ (s-tk/sonja-jolokia-jono "testijono-1" nil :purge)
        _ (s-tk/sonja-jolokia-jono "testijono-2" nil :purge)
        {:keys [lahetys-fn testijonokanava tila]} (:testikomponentti jarjestelma)
        {:keys [testijono-1 testijono-2]} @tila
        sonja (-> jarjestelma :sonja)
        _ (is (and testijono-1 testijono-2) "Testikomponentin jonoja ei keretty säätää oikein")
        {istunnot :istunnot} (-> sonja :tila deref :istunnot)
        testikomponentin-istunnot (select-keys istunnot ["istunto-testijono-1" "istunto-testijono-2"])
        sonja-broker-tila (fn [jonon-nimi attribuutti]
                            (-> (s-tk/sonja-jolokia-jono jonon-nimi attribuutti nil) :body (cheshire/decode) (get "value")))
        testikomponentin-lahettamat-viestit (into #{}
                                                  (repeatedly 100 #(gen/generate (s/gen ::testilahetys-viesti))))
        testijono-1-vastaanottamat-viestit (into #{}
                                                 (repeatedly 100 #(str "<harja:testi xmlns:harja=\"\">"
                                                                       "<viesti>jono-1" (gen/generate (s/gen ::testilahetys-viesti))
                                                                       "</viesti></harja:testi>")))
        testijono-2-vastaanottamat-viestit (into #{}
                                                 (repeatedly 100 #(str "<harja:testi xmlns:harja=\"\">"
                                                                       "<viesti>jono-2" (gen/generate (s/gen ::testilahetys-viesti))
                                                                       "</viesti></harja:testi>")))
        testijono-1-lahetys-fn #(s-tk/sonja-laheta "testijono-1" %)
        testijono-2-lahetys-fn #(s-tk/sonja-laheta "testijono-2" %)
        ;; Lähetetään viestejä rinnakkain testikomponentista brokerille
        testijonon-lahetys (thread (suorita-rinnakkain lahetys-fn testikomponentin-lahettamat-viestit))
        ;; Vastaanotetaan viestejä rinnakkain testijonoon 1
        testijonon-vastaanotto-1 (thread (suorita-rinnakkain testijono-1-lahetys-fn testijono-1-vastaanottamat-viestit))
        ;; Vastaanotetaan viestejä rinnakkain testijonoon 2
        testijonon-vastaanotto-2 (thread (suorita-rinnakkain testijono-2-lahetys-fn testijono-2-vastaanottamat-viestit))
        ;; Vastaaanotetaan viestejä tloikista
        ;; Otetaan kaikki ne viestit, joita käsiteltiin testikomponentissa
        kasitellyt-viestit (<!! (go-loop [saadut-viestit #{}]
                                  (if-let [uusi-viesti (first (alts!! [testijonokanava (timeout 3000)]))]
                                    (recur (conj saadut-viestit (clj-str/trim (.getText uusi-viesti))))
                                    saadut-viestit)))]
    (doseq [viesti-thread (<!! testijonon-lahetys)
            :let [viesti (<!! viesti-thread)]]
      (is (string? (re-find #"ID:.*" viesti))))
    (is (= (count testikomponentin-lahettamat-viestit) (- (sonja-broker-tila "testilahetys-jono" :enqueue-count)
                                                          (sonja-broker-tila "testilahetys-jono" :dequeue-count))))
    ;; Vain testijono-2 viestit pitäisi olla käsitelty, koska ykkönen nakkaa exceptionia
    (is (= kasitellyt-viestit testijono-2-vastaanottamat-viestit))
    ;; Tarkistetaan, että testijono-1:n vastaanottaja on kummiski vielä pystyssä
    (is (= "ACTIVE" (sonja/exception-wrapper (-> jarjestelma :sonja :tila deref :istunnot (get "istunto-testijono-1") :jonot (get "testijono-1") :vastaanottaja) getMessageListener)))))


(deftest main-komponentit-loytyy
  (is (s-tk/sonja-laheta-odota "tloik-ilmoitusviestijono" (slurp "resources/xsd/tloik/esimerkit/ilmoitus.xml"))))

(deftest jms-yhteys-kay-alhaalla
  (is (first (alts!! [*sonja-yhteys* (timeout 10000)])) "Yhteys ei alkanut 10 s sisällä")
  (let [kaskytyskanava (-> jarjestelma :sonja :kaskytyskanava)
        db (:db jarjestelma)
        tyyppi (-> asetukset :sonja :tyyppi)
        status-ennen (:vastaus (<!! (sonja/laheta-viesti-kaskytyskanavaan! kaskytyskanava {:jms-tilanne [tyyppi db]})))
        kysy-status (fn []
                      (loop [kertoja 1]
                        (when (< kertoja 5)
                          (let [vastaus (<!! (sonja/laheta-viesti-kaskytyskanavaan! kaskytyskanava {:jms-tilanne [tyyppi db]}))]
                            (if (= vastaus {:kaskytysvirhe :aikakatkaisu})
                              (recur (inc kertoja))
                              (:vastaus vastaus))))))
        _ (println "Lopetetetaan yhteys")
        _ (s-tk/sonja-jolokia-connection nil :stop)
        ;; Odotetaan hetki, että yhteys on pysäytetty
        _ (<!! (timeout 1500))
        status-lopetuksen-jalkeen (kysy-status)
        _ (println "Aloitetaan yhteys uudestaan")
        _ (s-tk/sonja-jolokia-connection nil :start)
        ;; Odotetaan hetki, että yhteys on aloitettu
        _ (<!! (go-loop [i 0]
                 (when (and (not= "ACTIVE" @sonja/jms-connection-tila)
                            (< i 5))
                   (<! (timeout 1000))
                   (recur (inc i)))))
        status-aloituksen-jalkeen (kysy-status)]

    ;; STATUS ENNEN TESTIT
    (is (= (-> status-ennen :olioiden-tilat :yhteyden-tila) "ACTIVE"))
    (doseq [istunto (-> status-ennen :olioiden-tilat :istunnot)]
      (is (= (:istunnon-tila istunto) "ACTIVE"))
      (is (= (-> istunto :jonot first vals first :vastaanottaja :vastaanottajan-tila) "ACTIVE")))
    ;; STATUS LOPETUKSEN JÄLKEEN TESTIT
    (is (= (-> status-lopetuksen-jalkeen :olioiden-tilat :yhteyden-tila) "RECONNECTING"))
    ;; STATUS RECONNECTIN JÄLKEEN
    (is (= (-> status-aloituksen-jalkeen :olioiden-tilat :yhteyden-tila) "ACTIVE"))
    (doseq [istunto (-> status-aloituksen-jalkeen :olioiden-tilat :istunnot)]
      (is (= (:istunnon-tila istunto) "ACTIVE"))
      (is (= (-> istunto :jonot first vals first :vastaanottaja :vastaanottajan-tila) "ACTIVE")))))

(deftest liikaa-kaskyja
  (alts!! [*sonja-yhteys* (timeout 10000)])
  (let [_ (s-tk/sonja-jolokia-jono "testilahetys-jono" nil :purge)
        {:keys [lahetys-fn]} (:testikomponentti jarjestelma)
        lahetykset (atom {:taynna 0 :ruuhkaa 0 :lahetettiin 0 :kaskyjen-kasittely 0})
        pitkaan-kestava-lahetys-kanava (chan)
        ;; Lähetetään pitkään kestävä viesti (yli Sonja ns määritellyn timeoutin)
        pitkaan-kestava-lahetys (with-redefs [sonja/viestin-kasittely-timeout 100
                                              sonja/laheta-viesti (fn [& args]
                                                                    (println "LÄHETETÄÄN HIDAS VIESTI")
                                                                    (>!! pitkaan-kestava-lahetys-kanava true)
                                                                    (<!! (timeout 500))
                                                                    (println "HIDAS VIESTI LÄHETETTY")
                                                                    true)]
                                  (thread (lahetys-fn "foo"))
                                  ;; Odotellaan, jotta with-redefs toimii
                                  (<!! pitkaan-kestava-lahetys-kanava))
        kasittele-kasky-ennen-redefs! sonja/kasittele-kasky!
        ;; Samalla kun pitkään kestävää viestiä lähetetään, lähetetään 120 muuta viestiä, joiden kaikkien pitäisi
        ;; epäonnistua
        muut-lahetykset (with-redefs [sonja/viestin-kasittely-timeout 100
                                      sonja/laheta-viesti (fn [& args]
                                                            ;; Jos viesti lähetetään, nostetaan counterin lukemaa.
                                                            ;; Tässä testissä viestejä ei tulisi lähettää yhtään.
                                                            (swap! lahetykset update :lahetettiin inc))
                                      sonja/kasittele-kasky! (fn [& args]
                                                               ;; Kun jms-säije sammutetaan, käsitellään jonossa jo
                                                               ;; olevat viestit ensin pois.
                                                               (swap! lahetykset update :kaskyjen-kasittely inc)
                                                               (apply kasittele-kasky-ennen-redefs! args))]
                          ;; Lähettään kerralla 120 viestiä ja odotellaan, niiden vastauksia
                          (doseq [uusi-lahetys (suorita-rinnakkain #(lahetys-fn %)
                                                                 (repeat 120 "bar"))]
                            ;; Käsittelykanavan koko on 100, niin sadasta viestistä pitäisi tulla valitusta, että
                            ;; timeout on mennyt loppuun ja 20 viestistä valitusta, että kanava on täynnä.
                            (case (<!! uusi-lahetys)
                              :taynna (swap! lahetykset update :taynna inc)
                              :ruuhkaa (swap! lahetykset update :ruuhkaa inc)))
                          ;; Sammutetaan jms-säije
                          (-> jarjestelma :sonja :saikeen-sammutus-kanava (>!! true))
                          ;; Sammuttaminen aiheuttaa ensin jo jonossa olevien viestien käsittelyn. Ne kaikki jonossa
                          ;; olevat 100 viestiä tulisi tulla timeout todetuiksi.
                          ;; Odotellaan tässä, että säije on sammutettu, jotta with-redefsit toimii.
                          (<!! (go-loop []
                                 (when-not (true? @sonja/jms-saije-sammutettu?)
                                   (<! (timeout 100))
                                   (recur)))))]
    (is (= 100 (:ruuhkaa @lahetykset)))
    (is (= 20 (:taynna @lahetykset)))
    (is (= 0 (:lahetettiin @lahetykset)))
    ;; 100 tulee jonossa ruuhkautuneita
    (is (= 100 (:kaskyjen-kasittely @lahetykset)))))
