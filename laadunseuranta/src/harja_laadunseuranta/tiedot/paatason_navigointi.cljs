(ns harja-laadunseuranta.tiedot.paatason-navigointi
  (:require [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.ui.ilmoitukset :as ilmoitukset]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [cljs-time.local :as l]
            [harja-laadunseuranta.utils :as utils]))

(def valilehti-talviset-pinnat
  [{:nimi "Liu\u00ADkas\u00ADta"
    :ikoni "talvi_liukasta"
    :tyyppi :vali
    :avain :liukasta
    :vaatii-nappaimiston? true}
   {:nimi "Lu\u00ADmis\u00ADta"
    :ikoni "talvi_lumista"
    :tyyppi :vali
    :avain :lumista
    :vaatii-nappaimiston? true}
   {:nimi "Tasaus\u00ADpuute"
    :tyyppi :vali
    :ikoni "talvi_tasauspuute"
    :avain :tasauspuute
    :vaatii-nappaimiston? true}
   {:nimi "Py\u00ADsäkki: epä\u00ADtasainen polanne"
    :tyyppi :piste
    :avain :pysakilla-epatasainen-polanne
    :ikoni "pysakki_epatasainen_polanne"
    :vaatii-nappaimiston? false}
   {:nimi "Py\u00ADsäkki auraa\u00ADmatta"
    :tyyppi :piste
    :avain :pysakki-auraamatta
    :ikoni "pysakki_auraamatta"
    :vaatii-nappaimiston? false}
   {:nimi "Py\u00ADsäkki hiekoit\u00ADtamatta"
    :tyyppi :piste
    :avain :pysakki-hiekoittamatta
    :ikoni "pysakki_hiekoittamatta"
    :vaatii-nappaimiston? false}])

(def valilehti-p-ja-l-alueet
  [{:nimi "Auraa\u00ADmatta"
    :tyyppi :piste
    :avain :pl-alue-auraamatta
    :ikoni "p_alue_auraamatta"
    :vaatii-nappaimiston? false}
   {:nimi "Hiekoit\u00ADtamatta"
    :tyyppi :piste
    :avain :pl-alue-hiekoittamatta
    :ikoni "p_alue_hiekoittamatta"
    :vaatii-nappaimiston? false}
   {:nimi "Epä\u00ADtasainen polanne"
    :tyyppi :piste
    :avain :pl-epatasainen-polanne
    :ikoni "p_alue_epatasainen_polanne"
    :vaatii-nappaimiston? false}
   {:nimi "Puhdis\u00ADtet\u00ADtava"
    :tyyppi :piste
    :ikoni "p_alue_puhdistettava"
    :avain :pl-alue-puhdistettava
    :vaatii-nappaimiston? false}
   {:nimi "Korjat\u00ADtavaa"
    :tyyppi :piste
    :ikoni "p_alue_korjattavaa"
    :avain :pl-alue-korjattavaa
    :vaatii-nappaimiston? false}
   {:nimi "Viher\u00ADalueet hoita\u00ADmatta"
    :tyyppi :piste
    :avain :viheralueet-hoitamatta
    :ikoni "p_alue_viheralue"
    :vaatii-nappaimiston? false}])

(def valilehti-reunat
  [{:nimi "Reuna\u00ADpaalu likai\u00ADnen"
    :tyyppi :piste
    :ikoni "reunapaalu_likainen"
    :avain :reunapaalut-likaisia
    :vaatii-nappaimiston? false}
   {:nimi "Reuna\u00ADpaalu vino\u00ADssa"
    :tyyppi :piste
    :avain :reunapaalut-vinossa
    :ikoni "reunapaalu_vinossa"
    :vaatii-nappaimiston? false}
   {:nimi "Rumpu tukossa"
    :tyyppi :piste
    :avain :rumpu-tukossa
    :ikoni "rumpu_tukossa"
    :vaatii-nappaimiston? false}
   {:nimi "Rumpu liettynyt"
    :tyyppi :piste
    :avain :rumpu-liettynyt
    :ikoni "rumpu_liettynyt"
    :vaatii-nappaimiston? false}
   {:nimi "Rumpi rikki"
    :tyyppi :piste
    :avain :rumpu-rikki
    :ikoni "rumpu_rikki"
    :vaatii-nappaimiston? false}
   {:nimi "Kaide\u00ADvaurio"
    :tyyppi :piste
    :avain :kaidevaurio
    :ikoni "kaidevaurio"
    :vaatii-nappaimiston? false}
   {:nimi "Kiveys\u00ADvaurio"
    :tyyppi :piste
    :avain :kiveysvaurio
    :ikoni "kiveysvaurio"
    :vaatii-nappaimiston? false}])

(def valilehti-viherhoito
  [{:nimi "Vesakko raivaa\u00ADmatta"
    :tyyppi :piste
    :ikoni "viheralue_raivaamatta"
    :avain :vesakko-raivaamatta
    :vaatii-nappaimiston? false}
   {:nimi "Niit\u00ADtämättä"
    :tyyppi :piste
    :avain :niittamatta
    :ikoni "viheralue_niittamatta"
    :vaatii-nappaimiston? false}
   {:nimi "Näkemä\u00ADalue raivaa\u00ADmatta"
    :tyyppi :piste
    :avain :nakemaalue-raivaamatta
    :ikoni "viheralue_nakemaalue_raivaamatta"
    :vaatii-nappaimiston? false}
   {:nimi "Liiken\u00ADnetila hoita\u00ADmatta"
    :tyyppi :piste
    :avain :liikennetila-hoitamatta
    :ikoni "viheralue_liikennetila_hoitamatta"
    :vaatii-nappaimiston? false}
   {:nimi "Istu\u00ADtukset hoita\u00ADmatta"
    :tyyppi :piste
    :avain :istutukset-hoitamatta
    :ikoni "viheralue_istutukset_hoitamatta"
    :vaatii-nappaimiston? false}])

(def valilehti-soratien-hoito
  [{:nimi "Sora\u00ADtie"
    :tyyppi :vali
    :ikoni "soratie_alkaa"
    :avain :soratie
    :vaatii-nappaimiston? false}
   {:nimi "Soran\u00ADpienta\u00ADreet: Reuna\u00ADpaletta"
    :tyyppi :piste
    :avain :reunapalletta
    :ikoni "soratie_reunapaletta"
    :vaatii-nappaimiston? false}
   {:nimi "Soran\u00ADpienta\u00ADreet: Reuna\u00ADtäyttö puutteel\u00ADlinen"
    :tyyppi :piste
    :avain :reunataytto-puutteellinen
    :vaatii-nappaimiston? false
    :ikoni "soratie_reunataytto_puutteellinen"}
   {:nimi "Soran\u00ADpienta\u00ADreet: Puutteel\u00ADlinen liuska\u00ADvaurio"
    :tyyppi :piste
    :avain :luiskavaurio
    :ikoni "sorapientareet_puuttellinen_liuskavaurio"
    :vaatii-nappaimiston? false}])

(def valilehti-muut
  [{:nimi "Liikenne\u00ADmerkki likai\u00ADnen"
    :ikoni "liikennemerkki_likainen"
    :tyyppi :piste
    :avain :liikennemerkki-likainen ;; TODO Aiemmin oli sama kuin luminen, puuttuuko tämä tietomallista?
    :vaatii-nappaimiston? false}
   {:nimi "Liikenne\u00ADmerkki vino\u00ADssa"
    :ikoni "liikennemerkki_vinossa"
    :tyyppi :piste
    :avain :liikennemerkki-vinossa
    :vaatii-nappaimiston? false}
   {:nimi "Liikenne\u00ADmerkki lumi\u00ADnen"
    :ikoni "liikennemerkki_luminen"
    :tyyppi :piste
    :avain :liikennemerkki-luminen
    :vaatii-nappaimiston? false}
   {:nimi "Oja: tukossa"
    :tyyppi :piste
    :avain :oja-tukossa
    :ikoni "oja_tukossa"
    :vaatii-nappaimiston? false}
   {:nimi "Oja: Ylijäämä\u00ADmassa tasattu huonosti"
    :avain :ylijaamamassa-tasattu-huonosti
    :tyyppi :piste
    :ikoni "oja_ylijaamamassa_tasattu_huonosti"
    :vaatii-nappaimiston? false}
   {:nimi "Oja: Kiviä poista\u00ADmatta"
    :tyyppi :piste
    :avain :ojat-kivia-poistamatta
    :ikoni "oja_kivia_poistamatta"
    :vaatii-nappaimiston? false}
   {:nimi "Silta: Puhdista\u00ADmatta"
    :ikoni "silta_puhdistamatta"
    :tyyppi :piste
    :avain :silta-puhdistamatta
    :vaatii-nappaimiston? false}
   {:nimi "Silta: Vaurioita"
    :tyyppi :piste
    :avain :siltavaurioita
    :ikoni "silta_vaurioita"
    :vaatii-nappaimiston? false}
   {:nimi "Silta: Saumoissa puutteita"
    :tyyppi :piste
    :avain :siltasaumoissa-puutteita
    :ikoni "silta_saumoissa_puutteita"
    :vaatii-nappaimiston? false}])

(def oletusvalilehdet
  [{:avain :talvihoito
    :nimi "Talviset pinnat"
    :sisalto valilehti-talviset-pinnat}
   {:avain :p-ja-l-alueet
    :nimi "P- ja L-alueet"
    :sisalto valilehti-p-ja-l-alueet}
   {:avain :reunat
    :nimi "Reunat"
    :sisalto valilehti-reunat}
   {:avain :viherhoito
    :nimi "Viherhoito"
    :sisalto valilehti-viherhoito}
   {:avain :soratien-hoito
    :nimi "Soratien hoito"
    :sisalto valilehti-soratien-hoito}
   {:avain :muut
    :nimi "Muut"
    :sisalto valilehti-muut}])

(defn kirjaa-pistemainen-havainto! [{:keys [nimi avain] :as tiedot}]
  (.log js/console (pr-str "Kirjataan pistemäinen havainto: " avain))
  (reset! s/pikavalinta avain)
  (ilmoitukset/ilmoita
    (str "Pistemäinen havainto kirjattu: " nimi))
  ;; TODO Kertakirjaus tehdään aina kun sijainti muuttuu, tässä tapauksessa halutaan
  ;; tehdä heti. Tässä on ehkä vähän duplikointia.
  ;; Voisiko tehdä niin, että aina kun sijainti muuttuu,
  ;; niin reitintallennus imaisee softan tilasta kaiken tarvittavan?
  ;; Ja reitintallennukselta voi myös erikseen pyytää tekemään imaisun heti, kuten nyt?
  (reitintallennus/kirjaa-kertakirjaus
    @s/idxdb
    {:havainnot {}
     :mittaukset {}
     :aikaleima (l/local-now)
     :pikavalinnan-kuvaus (@s/vakiohavaintojen-kuvaukset @s/pikavalinta)
     :pikavalinta @s/pikavalinta
     :sijainti (:nykyinen (utils/unreactive-deref s/sijainti))}
    @s/tarkastusajo-id))