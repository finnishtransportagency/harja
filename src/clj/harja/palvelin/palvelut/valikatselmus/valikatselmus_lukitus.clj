(ns harja.palvelin.palvelut.valikatselmus.valikatselmus-lukitus
  (:require [harja.pvm :as pvm]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.kyselyt.paatos-kyselyt :as paatos-kyselyt]
            [harja.palvelin.palvelut.valikatselmus.apurit :as v-apurit]
            [harja.palvelin.palvelut.valikatselmus.paatosnakyvyyskone :as paatoskone]))


(defn ominaisuuden-muokkaus-sallittu?
  "Sisältää säännöstön ominaisuuksittain, onko ominaisuuden muokkaus sallittu vai ei. Jos ominaisuus on lukittu, palautetaan false.
  Tukee ominaisuuksia: :tarjous, :uusi-kustannussuunnitelma, :tehtavat-ja-maarat, :kulu, :arvonvahennys, :sanktio, :bonus, :muutos ja :toteuma.
  Jos ei tue ominaisuutta, palauttaa aina true."
  [db urakka-id kuluva-hoitovuosi ominaisuus]
  (let [urakan-tiedot (first (q-urakat/hae-urakan-tiedot db urakka-id))
        urakan-alkuvuosi (-> urakan-tiedot :alkupvm pvm/vuosi)
        urakan-loppuvuosi (-> urakan-tiedot :loppupvm pvm/vuosi)
        mhu+urakka? (= "mhu+" (:sopimustyyppi urakan-tiedot))
        mhu-tyyppi (v-apurit/urakan-hoitotyyppi mhu+urakka?)
        kaikki-mahdolliset-paatokset (v-apurit/kaikki-mahdolliset-paatokset mhu-tyyppi urakan-alkuvuosi urakan-loppuvuosi kuluva-hoitovuosi)
        ;; Haetaan tietokantaan mahdollisesti tallennetut päätökset
        urakan-tehdyt-paatoset (paatos-kyselyt/hae-paatokset db kaikki-mahdolliset-paatokset urakka-id kuluva-hoitovuosi)
        ;; Muokataan tietokannasta haetut päätökset muotoon, jossa on vain päätösavain ja tieto siitä, onko päätös tehty
        ;; Palauttaa päätösavaimen ja tiedon, onko päätös tehty. Tyyliin -> [{:tavoitehinnan-muutokset true}]
        urakan-tehdyt-paatoset (map (fn [paatos] {(if (= :tavoitehinnan-pysyvat-muutokset (paatoskone/nimi->avain (:nimi paatos)))
                                                    :tavoitehinnan-muutokset
                                                    (paatoskone/nimi->avain (:nimi paatos)))
                                                  (boolean (:id paatos))}) urakan-tehdyt-paatoset)]

    (case ominaisuus
      (:tarjous :uusi-kustannussuunnitelma :tehtavat-ja-maarat :kulu :arvonvahennys :muutos :toteuma)
      (not-any? #(true? (:tavoitehinnan-muutokset %)) urakan-tehdyt-paatoset)
      (:sanktio :bonus)
      (not-any? #(true? (:valikatselmuspoytakirjaan-liitettavat-raportit %)) urakan-tehdyt-paatoset)
      :else true)))
