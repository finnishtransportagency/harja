(ns harja.palvelin.asetukset
  "Yleinen Harja-palvelimen konfigurointi. Esimerkkinä käytetty Antti Virtasen clj-weba."
  (:require [schema.core :as s]
            [clojure.string :as str]
            [taoensso.timbre :as log]
            [clojure.java.io :as io]
            [harja.palvelin.lokitus.slack :as slack]
            [taoensso.timbre.appenders.postal :refer [postal-appender]]))


(def Tietokanta {:palvelin s/Str
                 :tietokanta s/Str
                 :portti s/Int
                 (s/optional-key :yhteyspoolin-koko) s/Int
                 :kayttaja s/Str
                 :salasana s/Str})
(def Asetukset
  "Harja-palvelinasetuksien skeema"
  {(s/optional-key :sahke-headerit) {s/Str {s/Str s/Str}}
   :http-palvelin {:portti s/Int
                   :url s/Str
                   (s/optional-key :threads) s/Int
                   (s/optional-key :max-body-size) s/Int
                   (s/optional-key :anti-csrf-token) s/Str}
   :kehitysmoodi Boolean
   (s/optional-key :testikayttajat) [{:kayttajanimi s/Str :kuvaus s/Str}]
   :tietokanta Tietokanta
   :tietokanta-replica Tietokanta
   :fim {:url s/Str
         (s/optional-key :tiedosto) s/Str}
   :log {(s/optional-key :gelf) {:palvelin s/Str
                                 :taso s/Keyword}
         (s/optional-key :slack) {:webhook-url s/Str :taso s/Keyword
                                  (s/optional-key :urls) {(s/optional-key :ilog) {(s/optional-key :url) s/Str
                                                                                  (s/optional-key :tapahtuma-id) s/Str
                                                                                  (s/optional-key :alkanut) s/Str
                                                                                  (s/optional-key :valittu-jarjestelma) s/Str
                                                                                  (s/optional-key :valittu-integraatio) s/Str}
                                                          (s/optional-key :glog) {(s/optional-key :url) s/Str
                                                                                  (s/optional-key :from) s/Str
                                                                                  (s/optional-key :to) s/Str
                                                                                  (s/optional-key :q) s/Str
                                                                                  }
                                                          (s/optional-key :jira) [s/Str]}}

         (s/optional-key :email) {:taso s/Keyword
                                  :palvelin s/Str
                                  :vastaanottaja [s/Str]}
         (s/optional-key :testidata?) s/Bool}
   (s/optional-key :integraatiot) {:paivittainen-lokin-puhdistusaika [s/Num]}
   (s/optional-key :sonja) {:url s/Str
                            :kayttaja s/Str
                            :salasana s/Str
                            (s/optional-key :tyyppi) s/Keyword}
   (s/optional-key :sonja-sahkoposti) {:vastausosoite s/Str
                                       (s/optional-key :suora?) s/Bool
                                       (s/optional-key :palvelin) s/Str
                                       :jonot {(s/optional-key :sahkoposti-sisaan-jono) s/Str
                                               (s/optional-key :sahkoposti-ulos-jono) s/Str
                                               (s/optional-key :sahkoposti-ulos-kuittausjono) s/Str
                                               (s/optional-key :sahkoposti-ja-liite-ulos-jono) s/Str
                                               (s/optional-key :sahkoposti-ja-liite-ulos-kuittausjono) s/Str}}
   (s/optional-key :solita-sahkoposti) {:vastausosoite s/Str
                                        (s/optional-key :palvelin/Str) s/Str}
   (s/optional-key :sampo) {:lahetysjono-sisaan s/Str
                            :kuittausjono-sisaan s/Str
                            :lahetysjono-ulos s/Str
                            :kuittausjono-ulos s/Str
                            :paivittainen-lahetysaika [s/Num]}
   (s/optional-key :tloik) {:ilmoitusviestijono s/Str
                            :ilmoituskuittausjono s/Str
                            :toimenpideviestijono s/Str
                            :toimenpidekuittausjono s/Str
                            :uudelleenlahetysvali-minuuteissa s/Num
                            (s/optional-key :ilmoitukset) {:google-static-maps-key s/Str}}
   (s/optional-key :turi) {:turvallisuuspoikkeamat-url s/Str
                           (s/optional-key :urakan-tyotunnit-url) s/Str
                           :kayttajatunnus s/Str
                           :salasana s/Str
                           :paivittainen-lahetysaika [s/Num]}
   (s/optional-key :tierekisteri) {:url s/Str
                                   (s/optional-key :uudelleenlahetys-aikavali-minuutteina) s/Num}

   :ilmatieteenlaitos {:lampotilat-url s/Str}

   (s/optional-key :geometriapaivitykset) {(s/optional-key :tuontivali) s/Int
                                           (s/optional-key :kayttajatunnus) s/Str
                                           (s/optional-key :salasana) s/Str
                                           (s/optional-key :tieosoiteverkon-shapefile) s/Str
                                           (s/optional-key :tieosoiteverkon-osoite) s/Str
                                           (s/optional-key :tieosoiteverkon-tuontikohde) s/Str
                                           (s/optional-key :laajennetun-tieosoiteverkon-tiedot) s/Str
                                           (s/optional-key :pohjavesialueen-shapefile) s/Str
                                           (s/optional-key :pohjavesialueen-osoite) s/Str
                                           (s/optional-key :pohjavesialueen-tuontikohde) s/Str
                                           (s/optional-key :talvihoidon-hoitoluokkien-shapefile) s/Str
                                           (s/optional-key :talvihoidon-hoitoluokkien-osoite) s/Str
                                           (s/optional-key :talvihoidon-hoitoluokkien-tuontikohde) s/Str
                                           (s/optional-key :soratien-hoitoluokkien-shapefile) s/Str
                                           (s/optional-key :soratien-hoitoluokkien-osoite) s/Str
                                           (s/optional-key :soratien-hoitoluokkien-tuontikohde) s/Str
                                           (s/optional-key :siltojen-shapefile) s/Str
                                           (s/optional-key :siltojen-osoite) s/Str
                                           (s/optional-key :siltojen-tuontikohde) s/Str
                                           (s/optional-key :urakoiden-shapefile) s/Str
                                           (s/optional-key :urakoiden-osoite) s/Str
                                           (s/optional-key :urakoiden-tuontikohde) s/Str
                                           (s/optional-key :ely-alueiden-shapefile) s/Str
                                           (s/optional-key :ely-alueiden-osoite) s/Str
                                           (s/optional-key :ely-alueiden-tuontikohde) s/Str
                                           (s/optional-key :valaistusurakoiden-shapefile) s/Str
                                           (s/optional-key :valaistusurakoiden-osoite) s/Str
                                           (s/optional-key :valaistusurakoiden-tuontikohde) s/Str
                                           (s/optional-key :paallystyspalvelusopimusten-shapefile) s/Str
                                           (s/optional-key :paallystyspalvelusopimusten-osoite) s/Str
                                           (s/optional-key :paallystyspalvelusopimusten-tuontikohde) s/Str
                                           (s/optional-key :tekniset-laitteet-urakat-shapefile) s/Str
                                           (s/optional-key :tekniset-laitteet-urakat-osoite) s/Str
                                           (s/optional-key :tekniset-laitteet-urakat-tuontikohde) s/Str
                                           (s/optional-key :siltojenpalvelusopimusten-shapefile) s/Str
                                           (s/optional-key :siltojenpalvelusopimusten-osoite) s/Str
                                           (s/optional-key :siltojenpalvelusopimusten-tuontikohde) s/Str
                                           (s/optional-key :turvalaitteiden-shapefile) s/Str
                                           (s/optional-key :turvalaitteiden-osoite) s/Str
                                           (s/optional-key :turvalaitteiden-tuontikohde) s/Str
                                           (s/optional-key :kanavien-shapefile) s/Str
                                           (s/optional-key :kanavien-osoite) s/Str
                                           (s/optional-key :kanavien-tuontikohde) s/Str}

   (s/optional-key :yha) {:url s/Str
                          :kayttajatunnus s/Str
                          :salasana s/Str}

   (s/optional-key :velho) {:paallystetoteuma-url s/Str
                            :autorisaatio s/Str}

   (s/optional-key :labyrintti) {:url s/Str
                                 :kayttajatunnus s/Str
                                 :salasana s/Str}

   (s/optional-key :virustarkistus) {:url s/Str}
   (s/optional-key :tiedostopesula) {:base-url s/Str}

   (s/optional-key :paivystystarkistus) {:paivittainen-aika [s/Num]}
   (s/optional-key :reittitarkistus) {:paivittainen-aika [s/Num]}

   (s/optional-key :api-yhteysvarmistus) {(s/optional-key :ajovali-minuutteina) s/Int
                                          (s/optional-key :url) s/Str
                                          (s/optional-key :kayttajatunnus) s/Str
                                          (s/optional-key :salasana) s/Str}

   (s/optional-key :sonja-jms-yhteysvarmistus) {(s/optional-key :ajovali-minuutteina) s/Int
                                                (s/optional-key :jono) s/Str}

   (s/optional-key :pois-kytketyt-ominaisuudet) #{s/Keyword}

   (s/optional-key :sahke) {:lahetysjono s/Str
                            (s/optional-key :uudelleenlahetysaika) [s/Num]}

   (s/optional-key :turvalaitteet) {:geometria-url s/Str
                                    :paivittainen-tarkistusaika [s/Num]
                                    :paivitysvali-paivissa s/Num}

   (s/optional-key :vaylat) {:geometria-url s/Str
                             :paivittainen-tarkistusaika [s/Num]
                             :paivitysvali-paivissa s/Num}

   (s/optional-key :kanavasillat) {:geometria-url s/Str
                                   :paivittainen-tarkistusaika [s/Num]
                                   :paivitysvali-paivissa s/Num}

   (s/optional-key :tyotunti-muistutukset) {:paivittainen-aika [s/Num]}

   (s/optional-key :vkm) {:url s/Str}

   (s/optional-key :liitteet) {:fileyard-url s/Str}

   (s/optional-key :reimari) {:url s/Str
                              :kayttajatunnus s/Str
                              :salasana s/Str
                              (s/optional-key :toimenpidehakuvali) s/Int
                              (s/optional-key :komponenttityyppihakuvali) s/Int
                              (s/optional-key :turvalaitekomponenttihakuvali) s/Int
                              (s/optional-key :vikahakuvali) s/Int
                              (s/optional-key :turvalaiteryhmahakuaika) [s/Num]}


   (s/optional-key :ais-data) {:url s/Str
                               :sekunnin-valein s/Int}

   (s/optional-key :yllapitokohteet) {:paivittainen-sahkopostin-lahetysaika [s/Num]}})

(def oletusasetukset
  "Oletusasetukset paikalliselle dev-serverille"
  {:http-palvelin {:portti 3000 :url "http://localhost:3000/"
                   :threads 64
                   :max-body-size (* 1024 1024 16)}
   :kehitysmoodi true
   :tietokanta {:palvelin "localhost"
                :tietokanta "harja"
                :portti 5432
                :yhteyspoolin-koko 64
                :kayttaja "harja"
                :salasana ""}

   :log {:gelf {:palvelin "gl.solitaservices.fi" :taso :info}}
   :geometriapaivitykset {:tuontivali 100}
   })

(defn yhdista-asetukset [oletukset asetukset]
  (merge-with #(if (map? %1)
                 (merge %1 %2)
                 %2)
              oletukset asetukset))

(defn tarkista-asetukset [asetukset]
  (s/check Asetukset asetukset))

(defn tarkista-ymparisto! []
  (let [env-muuttujat (with-open [rdr (io/reader ".harja_env")]
                        (reduce (fn [tulos rivi]
                                  (conj tulos (str/trim rivi)))
                                []
                                (line-seq rdr)))]
    (doseq [env-muuttuja env-muuttujat]
      (when (nil? (System/getenv env-muuttuja))
        (log/error (str "Ympäristömuuttujaa " env-muuttuja " ei ole asetettu!"))))))

(defn lue-asetukset
  "Lue Harja palvelimen asetukset annetusta tiedostosta ja varmista, että ne ovat oikeat"
  [tiedosto]
  (->> tiedosto
       slurp
       read-string
       (yhdista-asetukset oletusasetukset)))

(defonce pois-kytketyt-ominaisuudet (atom #{}))

(defn ominaisuus-kaytossa? [k]
  (let [pko @pois-kytketyt-ominaisuudet]
    (if (nil? pko)
      false
      (not (contains? pko k)))))

(defn aseta-kaytettavat-ominaisuudet! [pois-kytketyt-ominaisuudet-joukko]
  (reset! pois-kytketyt-ominaisuudet pois-kytketyt-ominaisuudet-joukko))

(defn crlf-filter [msg]
  (assoc msg :vargs (mapv (fn [s]
                            (if (string? s)
                              (str/replace s #"[\n\r]" "")
                              s))
                          (:vargs msg))))

(defn- logituksen-tunnus [msg]
  (let [ensimmainen-arg (-> msg :vargs first)
        tunnus (when (string? ensimmainen-arg)
                 (second
                   (re-find #"^\[([^\]]*)\]"
                            ensimmainen-arg)))]
    (when tunnus
      (str/lower-case tunnus))))

(defn logitetaanko
  "Tämän palauttama middleware on hyödyllinen, jos testidatan puuttellisuus aiheuttaa suuret määrät logitusta turhaan.
   Esimerkiksi siltojen tuonnissa halutaan logittaa, jos datassa on jotain ongelmia tuotannossa, mutta testidatan
   kanssa tämä logitus aiheuttaa tuhansia logituksia turhaan."
  [{:keys [testidata? ei-logiteta]}]
  (fn [msg]
    (let [ei-logiteta? (when-let [tunnus (logituksen-tunnus msg)]
                         (and (contains? ei-logiteta tunnus)
                              testidata?))]
      (when-not ei-logiteta?
        msg))))

(defn konfiguroi-lokitus [asetukset]
  (log/merge-config! {:middleware [(logitetaanko (:log asetukset))
                                   crlf-filter]})

  (when-not (:kehitysmoodi asetukset)
    (log/merge-config! {:appenders {:println {:min-level :info}}}))

  (when-let [gelf (-> asetukset :log :gelf)]
    (log/merge-config! {:shared-appender-config {:gelf {:host (:palvelin gelf)}}}))

  (when-let [slack (-> asetukset :log :slack)]
    (log/merge-config! {:appenders
                        {:slack
                         (slack/luo-slack-appender (str/trim (:webhook-url slack))
                                                   (:taso slack)
                                                   (:urls slack))}}))

  (when-let [email (-> asetukset :log :email)]
    (log/merge-config!
      {:appenders
       {:postal
        (postal-appender
          ^{:host (:palvelin email)}
          {:from (str (.getHostName (java.net.InetAddress/getLocalHost)) "@solita.fi")
           :to (:vastaanottaja email)})}})))
