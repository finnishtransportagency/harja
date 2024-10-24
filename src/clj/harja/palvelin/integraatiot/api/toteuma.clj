(ns harja.palvelin.integraatiot.api.toteuma
  "Toteuman kirjaaminen urakalle"
  (:require [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.kyselyt.materiaalit :as materiaalit]
            [harja.kyselyt.toteumat :as q-toteumat]
            [harja.kyselyt.toimenpidekoodit :as q-toimenpidekoodi]
            [harja.kyselyt.sopimukset :as sopimukset]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.validointi.toteumat :as validointi]
            [harja.domain.reittipiste :as rp]
            [clojure.java.jdbc :as jdbc]
            [harja.pvm :as pvm])
  (:use [slingshot.slingshot :only [throw+]]))

(defn hae-toteuman-kaikki-sopimus-idt [toteumatyyppi-yksikko toteumatyyppi-monikko data]
  (keep identity
        (reduce
          conj
          [(get-in data [toteumatyyppi-yksikko :toteuma :sopimusId])]
          (mapv
            #(get-in % [toteumatyyppi-yksikko :toteuma :sopimusId])
            (toteumatyyppi-monikko data)))))


(defn hae-sopimus-id [db urakka-id toteuma]
  (let [sopimus-id (or (:sopimusId toteuma) (:id (first (sopimukset/hae-urakan-paasopimus db urakka-id))))]
    (if sopimus-id
      sopimus-id
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+sopimusta-ei-loydy+
                          :viesti (format "Urakalle (id: %s.) ei löydy sopimusta" urakka-id)}]}))))

(defn paivita-toteuma [db urakka-id kirjaaja toteuma tyokone]
  (log/debug "Päivitetään vanha toteuma, jonka ulkoinen id on " (get-in toteuma [:tunniste :id]))
  (validointi/validoi-ajan-vuosi (:alkanut toteuma))
  (validointi/validoi-ajan-vuosi (:paattynyt toteuma))
  (validointi/validoi-toteuman-pvm-vali (:alkanut toteuma) (:paattynyt toteuma))
  (validointi/tarkista-tehtavat db urakka-id (:tehtavat toteuma) (:toteumatyyppi toteuma))
  (let [sopimus-id (hae-sopimus-id db urakka-id toteuma)
        paivitetty (q-toteumat/paivita-toteuma-ulkoisella-idlla<!
                     db
                     {:alkanut (aika-string->java-sql-date (:alkanut toteuma))
                      :paattynyt (aika-string->java-sql-date (:paattynyt toteuma))
                      :kayttaja (:id kirjaaja)
                      :suorittajan_nimi (get-in toteuma [:suorittaja :nimi])
                      :ytunnus (get-in toteuma [:suorittaja :ytunnus])
                      :lisatieto (:lisatieto toteuma)
                      :tyyppi (:toteumatyyppi toteuma)
                      :sopimus sopimus-id
                      :id (get-in toteuma [:tunniste :id])
                      :urakka urakka-id
                      :luoja (:id kirjaaja)
                      :tyokonetyyppi (:tyokonetyyppi tyokone)
                      :tyokonetunniste (:id tyokone)
                      :tyokoneen-lisatieto (:tunnus tyokone)})
        toteuman-id (if paivitetty
                      (:id paivitetty)
                      (q-toteumat/toteuman-id-ulkoisella-idlla db {:ulkoinen_id (get-in toteuma [:tunniste :id])}))]
    toteuman-id))

(defn poista-toteumat [db kirjaaja ulkoiset-idt urakka-id]
  (log/debug "Poistetaan luojan" (:id kirjaaja) "toteumat, joiden ulkoiset idt ovat" ulkoiset-idt " urakka-id: " urakka-id)
  (jdbc/with-db-transaction [db db]
    (let [kayttaja-id (:id kirjaaja)
          toteumien-alkupvmt (set (map #(pvm/pvm (:alkanut %))
                                       (q-toteumat/hae-poistettavien-toteumien-alkanut-ulkoisella-idlla db {:urakka-id urakka-id
                                                                                                            :ulkoiset-idt ulkoiset-idt})))
          poistettujen-maara (q-toteumat/poista-toteumat-ulkoisilla-idlla-ja-luojalla! db kayttaja-id ulkoiset-idt urakka-id)

          sopimus-idt (map :id (sopimukset/hae-urakan-sopimus-idt db {:urakka_id urakka-id}))]
      (log/debug "Poistettujen määrä:" poistettujen-maara)
      (when (and (> poistettujen-maara 0)
                 (> (count sopimus-idt) 0))
        (do
          ;; Päivitetään sopimuksiin liittyvät materiaalien käytöt
          (doseq [sopimus-id sopimus-idt]
            (doseq [alkupvm toteumien-alkupvmt]
              (log/debug "paivita-sopimuksen-materiaalin-kaytto sopimus-id:lle: " sopimus-id " alkupvm: " (pvm/->pvm alkupvm) " urakkaid:" urakka-id)
              (materiaalit/paivita-sopimuksen-materiaalin-kaytto db {:sopimus sopimus-id
                                                                     :alkupvm (pvm/->pvm alkupvm)
                                                                     :urakkaid urakka-id})))
          ;; Päivitetään urakoihin liittyvät materiaalin käytöt
          (log/debug "paivita_urakan_materiaalin_kaytto_hoitoluokittain urakka-id:lle: " urakka-id
            " alkupvm: " (pvm/->pvm (first toteumien-alkupvmt)) " loppupvm: "(pvm/->pvm (last toteumien-alkupvmt)))
          (materiaalit/paivita-urakan-materiaalin-kaytto-hoitoluokittain db {:urakka urakka-id
                                                                             :alkupvm (pvm/->pvm (first toteumien-alkupvmt))
                                                                             :loppupvm (pvm/->pvm (last toteumien-alkupvmt))})))
      (let [ilmoitukset (if (pos? poistettujen-maara)
                          (format "Toteumat poistettu onnistuneesti. Poistettiin: %s toteumaa." poistettujen-maara)
                          "Tunnisteita vastaavia toteumia ei löytynyt käyttäjän kirjaamista urakan toteumista.")]
        (tee-kirjausvastauksen-body {:ilmoitukset ilmoitukset})))))

(defn luo-uusi-toteuma [db urakka-id kirjaaja toteuma tyokone]
  (log/debug "Luodaan uusi toteuma.")
  (validointi/validoi-ajan-vuosi (:alkanut toteuma))
  (validointi/validoi-ajan-vuosi (:paattynyt toteuma))
  (validointi/validoi-toteuman-pvm-vali (:alkanut toteuma) (:paattynyt toteuma))
  (validointi/tarkista-tehtavat db urakka-id (:tehtavat toteuma) (:toteumatyyppi toteuma))
  (let [sopimus-id (hae-sopimus-id db urakka-id toteuma)]
    (do
      (q-toteumat/luo-toteuma<!
           db
           {:urakka urakka-id
            :sopimus sopimus-id
            :alkanut (aika-string->java-sql-date (:alkanut toteuma))
            :paattynyt (aika-string->java-sql-date (:paattynyt toteuma))
            :tyyppi (:toteumatyyppi toteuma)
            :kayttaja (:id kirjaaja)
            :suorittaja (get-in toteuma [:suorittaja :nimi])
            :ytunnus (get-in toteuma [:suorittaja :ytunnus])
            :lisatieto (:lisatieto toteuma)
            :ulkoinen_id (get-in toteuma [:tunniste :id])
            :reitti (:reitti toteuma),
            :numero nil
            :alkuosa nil
            :alkuetaisyys nil
            :loppuosa nil
            :loppuetaisyys nil
            :lahde "harja-api"
            :tyokonetyyppi (:tyokonetyyppi tyokone)
            :tyokonetunniste (:id tyokone)
            :tyokoneen-lisatieto (:tunnus tyokone)})
      (q-toteumat/luodun-toteuman-id db))))

(defn paivita-tai-luo-uusi-toteuma
  ([db urakka-id kirjaaja toteuma] (paivita-tai-luo-uusi-toteuma db urakka-id kirjaaja toteuma nil))
  ([db urakka-id kirjaaja toteuma tyokone]
   (if (q-toteumat/onko-olemassa-ulkoisella-idlla? db (get-in toteuma [:tunniste :id]) urakka-id)
     (paivita-toteuma db urakka-id kirjaaja toteuma tyokone)
     (luo-uusi-toteuma db urakka-id kirjaaja toteuma tyokone))))

(defn paivita-toteuman-reitti [db toteuma-id reitti]
  ;; Tuotantoon on lokakuun 2016 alussa toteumia, joilla pitäisi olla reitti, mutta ei ole.
  ;; Vaikea saada virheestä kiinni, mutta logitetaan tässä, jos tyhjä reitti tallennetan.
  ;; Pitää huomata, että periaatteessa voimme oikeasti halutakkin tallentaa tyhjän reitin..
  (when-not reitti (log/warn "Toteumalle " toteuma-id " tallennetaan tyhjä reitti!"))
  (q-toteumat/paivita-toteuman-reitti! db {:id toteuma-id
                                           :reitti reitti}))

(defn tallenna-sijainti [db sijainti aika toteuma-id tehtavat materiaalit]
  (log/debug "Luodaan toteumalle uusi sijainti reittipisteenä")
  (q-toteumat/tallenna-toteuman-reittipisteet!
   db
   {::rp/toteuma-id toteuma-id
    ::rp/reittipisteet
    [(rp/reittipiste aika
                     (:koordinaatit sijainti)
                     (q-toteumat/pisteen-hoitoluokat db (:koordinaatit sijainti) tehtavat materiaalit))]}))

(defn tallenna-tehtavat [db kirjaaja toteuma toteuma-id urakka-id]
      (log/debug (str "Tuhotaan toteuman vanhat tehtävät. Toteuma id: " toteuma-id))
      (q-toteumat/poista-toteuma_tehtava-toteuma-idlla! db toteuma-id)
      (log/debug "Luodaan toteumalle uudet tehtävät")
      (doseq [tehtava (:tehtavat toteuma)]
             (log/debug "Luodaan tehtävä.")
             (let [tehtava-id (q-toimenpidekoodi/hae-tehtava-apitunnisteella db
                                (get-in tehtava [:tehtava :id]) urakka-id)]
                  (q-toteumat/luo-toteuma_tehtava<!
                    db
                    toteuma-id
                    tehtava-id
                    (get-in tehtava [:tehtava :maara :maara])
                    (:id kirjaaja)
                    nil
                    nil
                    urakka-id))))

;; Konvertoi apilta tulevan materiaalinimen tietokannassa olevaan materiaaliin
(def mat-apilta->mat-db
  {;; Talvisuolat: Talvisuola rakeinen, voi tulla kahdella eri nimellä, koska tuetaan myös aiemmin speksattuja nimiä
   "Talvisuola" "Talvisuola, rakeinen NaCl"
   "Talvisuola, rakeinen NaCl" "Talvisuola, rakeinen NaCl"
   "Talvisuolaliuos CaCl2" "Talvisuolaliuos CaCl2"
   "Talvisuolaliuos NaCl" "Talvisuolaliuos NaCl"

   ;; Erityisalueiden nimet eivät ole päivittyneet, joten mäpätään saapuvat tietokantaan, ikäänkuin suoraan
   "Erityisalueet CaCl2-liuos" "Erityisalueet CaCl2-liuos"
   "Erityisalueet NaCl" "Erityisalueet NaCl"
   "Erityisalueet NaCl-liuos" "Erityisalueet NaCl-liuos"

   ;; Hiekoitushiekan nimeä ei ole muutettu
   "Hiekoitushiekan suola" "Hiekoitushiekan suola"

   ;; Kaliumformiaatti voi tulla sekä liuosnimellä että ilman liuosnimeä. Molemmat on sama asia
   "Kaliumformiaatti" "Kaliumformiaattiliuos"
   "Kaliumformiaattiliuos" "Kaliumformiaattiliuos"
   "Natriumformiaatti" "Natriumformiaatti"
   "Natriumformiaattiliuos" "Natriumformiaattiliuos"

   ;; Kesäsuolan materiaalinimet ovat päivittyneet. Otetaan ne sisään sekä vanhalla, että uudella nimellä
   "Kesäsuola (sorateiden kevätkunnostus)" "Kesäsuola sorateiden kevätkunnostus"
   "Kesäsuola sorateiden kevätkunnostus" "Kesäsuola sorateiden kevätkunnostus"
   ;; Sorateiden pölynsidonta on yleisen materiaali, joten mäpätään vanha nimi uuteen
   "Kesäsuola" "Kesäsuola sorateiden pölynsidonta"
   "Kesäsuola (pölynsidonta)" "Kesäsuola sorateiden pölynsidonta"
   "Kesäsuola sorateiden pölynsidonta" "Kesäsuola sorateiden pölynsidonta"
   ;; Päällystettyjen teiden pölynsidonta on uusi materiaali
   "Kesäsuola päällystettyjen teiden pölynsidonta" "Kesäsuola päällystettyjen teiden pölynsidonta"

   "Hiekoitushiekka" "Hiekoitushiekka"

   "Jätteet kaatopaikalle" "Jätteet kaatopaikalle"
   "Rikkaruohojen torjunta-aineet" "Rikkaruohojen torjunta-aineet"
   ;; Murskeet: Sorastusmurske on yleisin murkse, joten mäpätään murske aina sorastusmurskeeksi.
   "Murskeet" "Sorastusmurske"
   "Murske" "Sorastusmurske"
   "Sorastusmurske" "Sorastusmurske"
   ;; Muut murskeet saavat tulla omilla nimillään
   "Reunantäyttömurske" "Reunantäyttömurske"
   "Kelirikkomurske" "Kelirikkomurske"})

(defn hae-materiaalikoodi-nimella [db materiaali-nimi]
  (when-not (nil? (mat-apilta->mat-db materiaali-nimi))
    (:id (first (materiaalit/hae-materiaalikoodin-id-nimella db (mat-apilta->mat-db materiaali-nimi))))))

(defn tallenna-materiaalit [db kirjaaja toteuma toteuma-id urakka-id]
  (log/debug "Tuhotaan toteuman vanhat materiaalit. Toteuma id: " toteuma-id)
  (q-toteumat/poista-toteuma-materiaali-toteuma-idlla! db toteuma-id)
  (log/debug "Luodaan toteumalle uudet materiaalit")
  (doseq [materiaali (:materiaalit toteuma)]
    (log/debug "Etsitään materiaalikoodi kannasta.")
    (let [materiaali-nimi (:materiaali materiaali)
          materiaalikoodi-id (hae-materiaalikoodi-nimella db materiaali-nimi)]
      (if (nil? materiaalikoodi-id)
        (throw+ {:type virheet/+sisainen-kasittelyvirhe+
                 :virheet [{:koodi virheet/+tuntematon-materiaali+
                            :viesti (format "Tuntematon materiaali: %s." materiaali-nimi)}]}))
      (q-toteumat/luo-toteuma-materiaali<!
        db
        toteuma-id
        materiaalikoodi-id
        (get-in materiaali [:maara :maara])
        (:id kirjaaja)
        urakka-id))))
