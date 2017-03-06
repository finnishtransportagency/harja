(ns harja.palvelin.integraatiot.api.toteuma
  "Toteuman kirjaaminen urakalle"
  (:require [compojure.core :refer [POST GET]]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-reitti poista-palvelut]]
            [harja.palvelin.integraatiot.api.tyokalut.kutsukasittely :refer [kasittele-kutsu tee-kirjausvastauksen-body]]
            [harja.kyselyt.materiaalit :as materiaalit]
            [harja.kyselyt.toteumat :as q-toteumat]
            [harja.kyselyt.sopimukset :as sopimukset]
            [harja.palvelin.integraatiot.api.tyokalut.json :refer [aika-string->java-sql-date]]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.api.validointi.toteumat :as validointi])
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

(defn paivita-toteuma [db urakka-id kirjaaja toteuma]
  (log/debug "Päivitetään vanha toteuma, jonka ulkoinen id on " (get-in toteuma [:tunniste :id]))
  (validointi/validoi-toteuman-pvm-vali (:alkanut toteuma) (:paattynyt toteuma))
  (validointi/tarkista-tehtavat db (:tehtavat toteuma) (:toteumatyyppi toteuma))
  (let [sopimus-id (hae-sopimus-id db urakka-id toteuma)]
    (:id (q-toteumat/paivita-toteuma-ulkoisella-idlla<!
           db
           {:alkanut (aika-string->java-sql-date (:alkanut toteuma))
            :paattynyt (aika-string->java-sql-date (:paattynyt toteuma))
            :kayttaja (:id kirjaaja)
            :suorittajan_nimi (get-in toteuma [:suorittaja :nimi])
            :ytunnus (get-in toteuma [:suorittaja :ytunnus])
            :lisatieto ""
            :tyyppi (:toteumatyyppi toteuma)
            :sopimus sopimus-id
            :id (get-in toteuma [:tunniste :id])
            :urakka urakka-id}))))

(defn poista-toteumat [db kirjaaja ulkoiset-idt]
  (log/debug "Poistetaan luojan" (:id kirjaaja) "toteumat, joiden ulkoiset idt ovat" ulkoiset-idt)
  (let [kayttaja-id (:id kirjaaja)
        poistettujen-maara (q-toteumat/poista-toteumat-ulkoisilla-idlla-ja-luojalla! db kayttaja-id ulkoiset-idt)]
    (log/debug "Poistettujen maara:" poistettujen-maara)
    (let [ilmoitukset (if (pos? poistettujen-maara)
                        (format "Toteumat poistettu onnistuneesti. Poistettiin: %s toteumaa." poistettujen-maara)
                        "Tunnisteita vastaavia toteumia ei löytynyt käyttäjän kirjaamista toteumista.")]
      (tee-kirjausvastauksen-body {:ilmoitukset ilmoitukset}))))

(defn luo-uusi-toteuma [db urakka-id kirjaaja toteuma]
  (log/debug "Luodaan uusi toteuma.")
  (validointi/validoi-toteuman-pvm-vali (:alkanut toteuma) (:paattynyt toteuma))
  (validointi/tarkista-tehtavat db (:tehtavat toteuma) (:toteumatyyppi toteuma))
  (let [sopimus-id (hae-sopimus-id db urakka-id toteuma)]
    (:id (q-toteumat/luo-toteuma<!
           db
           urakka-id
           sopimus-id
           (aika-string->java-sql-date (:alkanut toteuma))
           (aika-string->java-sql-date (:paattynyt toteuma))
           (:toteumatyyppi toteuma)
           (:id kirjaaja)
           (get-in toteuma [:suorittaja :nimi])
           (get-in toteuma [:suorittaja :ytunnus])
           ""
           (get-in toteuma [:tunniste :id])
           (:reitti toteuma)
           nil nil nil nil nil
           "harja-api"))))

(defn paivita-tai-luo-uusi-toteuma [db urakka-id kirjaaja toteuma]
  (if (q-toteumat/onko-olemassa-ulkoisella-idlla? db (get-in toteuma [:tunniste :id]) (:id kirjaaja))
    (paivita-toteuma db urakka-id kirjaaja toteuma)
    (luo-uusi-toteuma db urakka-id kirjaaja toteuma)))

(defn paivita-toteuman-reitti [db toteuma-id reitti]
  ;; Tuotantoon on lokakuun 2016 alussa toteumia, joilla pitäisi olla reitti, mutta ei ole.
  ;; Vaikea saada virheestä kiinni, mutta logitetaan tässä, jos tyhjä reitti tallennetan.
  ;; Pitää huomata, että periaatteessa voimme oikeasti halutakkin tallentaa tyhjän reitin..
  (when-not reitti (log/warn "Toteumalle " toteuma-id " tallennetaan tyhjä reitti!"))
  (q-toteumat/paivita-toteuman-reitti! db {:id toteuma-id
                                           :reitti reitti}))

(defn tallenna-sijainti [db sijainti aika toteuma-id]
  (log/debug "Tuhotaan toteuman " toteuma-id " vanha sijainti")
  (q-toteumat/poista-reittipiste-toteuma-idlla! db toteuma-id)
  (log/debug "Luodaan toteumalle uusi sijainti reittipisteenä")
  (q-toteumat/luo-reittipiste<! db toteuma-id aika
                                (get-in sijainti [:koordinaatit :x])
                                (get-in sijainti [:koordinaatit :y])))

(defn tallenna-tehtavat [db kirjaaja toteuma toteuma-id]
  (log/debug "Tuhotaan toteuman vanhat tehtävät")
  (q-toteumat/poista-toteuma_tehtava-toteuma-idlla!
    db
    toteuma-id)
  (log/debug "Luodaan toteumalle uudet tehtävät")
  (doseq [tehtava (:tehtavat toteuma)]
    (log/debug "Luodaan tehtävä.")
    (q-toteumat/luo-toteuma_tehtava<!
      db
      toteuma-id
      (get-in tehtava [:tehtava :id])
      (get-in tehtava [:tehtava :maara :maara])
      (:id kirjaaja)
      nil
      nil)))

(defn tallenna-materiaalit [db kirjaaja toteuma toteuma-id]
  (log/debug "Tuhotaan toteuman vanhat materiaalit")
  (q-toteumat/poista-toteuma-materiaali-toteuma-idlla! db toteuma-id)
  (log/debug "Luodaan toteumalle uudet materiaalit")
  (doseq [materiaali (:materiaalit toteuma)]
    (log/debug "Etsitään materiaalikoodi kannasta.")
    (let [materiaali-nimi (:materiaali materiaali)
          materiaalikoodi-id (:id (first (materiaalit/hae-materiaalikoodin-id-nimella db materiaali-nimi)))]
      (if (nil? materiaalikoodi-id)
        (throw+ {:type virheet/+sisainen-kasittelyvirhe+
                 :virheet [{:koodi virheet/+tuntematon-materiaali+
                            :viesti (format "Tuntematon materiaali: %s." materiaali-nimi)}]}))
      (q-toteumat/luo-toteuma-materiaali<!
        db
        toteuma-id
        materiaalikoodi-id
        (get-in materiaali [:maara :maara])
        (:id kirjaaja))))
  ;; Päivitä sopimuksen päivän materiaalinkäyttö
  (materiaalit/paivita-sopimuksen-materiaalin-kaytto db (:sopimusId toteuma) (:alkanut toteuma)))
