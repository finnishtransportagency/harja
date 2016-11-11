(ns harja-laadunseuranta.tiedot.paanavigointi
  (:require [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.ui.ilmoitukset :as ilmoitukset]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [cljs-time.local :as l]
            [harja-laadunseuranta.utils :as utils]
            [cljs-time.coerce :as tc]
            [cljs-time.local :as lt]))

(def valilehti-talviset-pinnat
  [{:nimi "Liu\u00ADkas\u00ADta"
    :ikoni "talvi_liukasta"
    :tyyppi :vali
    :avain :liukasta
    :mittaus {:nimi "Liukkaus"
              :tyyppi :kitkamittaus
              :yksikko nil}
    :vaatii-nappaimiston? true}
   {:nimi "Lu\u00ADmis\u00ADta"
    :ikoni "talvi_lumista"
    :tyyppi :vali
    :avain :lumista
    :mittaus {:nimi "Lumisuus"
              :tyyppi :lumisuus
              :yksikko "cm"}
    :vaatii-nappaimiston? true}
   {:nimi "Tasaus\u00ADpuute"
    :avain :talvihoito-tasaisuus
    :tyyppi :vali
    :ikoni "talvi_tasauspuute"
    :mittaus {:nimi "Tasauspuute"
              :tyyppi :talvihoito-tasaisuus
              :yksikko "cm"}
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

(def valilehti-liikennemerkit
  [{:nimi "Liikenne\u00ADmerkki likai\u00ADnen"
    :ikoni "liikennemerkki_likainen"
    :tyyppi :piste
    :avain :liikennemerkki-likainen
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
    :vaatii-nappaimiston? false}])

(def valilehti-viherhoito
  [{:nimi "Vesakko raivaa\u00ADmatta"
    :tyyppi :vali
    :ikoni "viheralue_raivaamatta"
    :avain :vesakko-raivaamatta
    :vaatii-nappaimiston? false}
   {:nimi "Niit\u00ADtämättä"
    :tyyppi :vali
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
    :vaatii-nappaimiston? false}
   {:nimi "P- tai L-alueet hoitamatta"
    :tyyppi :piste
    :avain :pl-alue-hoitamatta
    :ikoni "p_tai_l_alue_hoitamatta"
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
   {:nimi "Oja tukossa"
    :tyyppi :piste
    :avain :oja-tukossa
    :ikoni "oja_tukossa"
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
    :vaatii-nappaimiston? false}
   {:nimi "Reuna\u00ADpalletta"
    :tyyppi :vali
    :avain :reunapalletta
    :ikoni "soratie_reunapalletta"
    :vaatii-nappaimiston? false}
   {:nimi "Reuna\u00ADtäyttö puutteel\u00ADlinen"
    :tyyppi :vali
    :avain :reunataytto-puutteellinen
    :vaatii-nappaimiston? false
    :ikoni "soratie_reunataytto_puutteellinen"}])

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
    :vaatii-nappaimiston? false}])

(def valilehti-sillat
  [{:nimi "Puhdista\u00ADmatta"
    :ikoni "silta_puhdistamatta"
    :tyyppi :piste
    :avain :silta-puhdistamatta
    :vaatii-nappaimiston? false}
   {:nimi "Sau\u00ADmoissa puut\u00ADteita"
    :tyyppi :piste
    :avain :siltasaumoissa-puutteita
    :ikoni "silta_saumoissa_puutteita"
    :vaatii-nappaimiston? false}
   {:nimi "Pääl\u00ADlys\u00ADtees\u00ADsä vaurioita"
    :tyyppi :piste
    :avain :sillan-paallysteessa-vaurioita
    :vaatii-nappaimiston? false}
   {:nimi "Kaide\u00ADvauri\u00ADoita"
    :tyyppi :piste
    :avain :sillassa-kaidevaurioita
    :ikoni "silta_vaurioita"
    :vaatii-nappaimiston? false}
   {:nimi "Reuna\u00ADpalkki\u00ADvauri\u00ADoita"
    :tyyppi :piste
    :avain :sillassa-reunapalkkivaurioita
    :vaatii-nappaimiston? false}])

(def valilehti-soratiet
  [{:nimi "Sora\u00ADtie"
    :tyyppi :vali
    :ikoni "soratie_alkaa"
    :avain :soratie
    :vaatii-nappaimiston? false}]) ;; TODO Oikeasti true, mutta vaatii oman erikoisnäppäimistön

(def valilehti-paallystys
  [{:nimi "Sauma\u00ADvirhe"
    :tyyppi :vali
    :ikoni "paallystys_saumavirhe"
    :avain :saumavirhe
    :vaatii-nappaimiston? false}
   {:nimi "Lajit\u00ADtuma"
    :tyyppi :vali
    :ikoni "paallystys_lajittuma"
    :avain :lajittuma
    :vaatii-nappaimiston? false}
   {:nimi "Epä\u00ADtasai\u00ADsuus"
    :tyyppi :vali
    :ikoni "paallystys_epatasaisuus"
    :avain :epatasaisuus
    :vaatii-nappaimiston? false}
   {:nimi "Hal\u00ADkeamat"
    :tyyppi :vali
    :ikoni "paallystys_halkeama"
    :avain :halkeamat
    :vaatii-nappaimiston? false}
   {:nimi "Vesi\u00ADlammi\u00ADkot"
    :tyyppi :vali
    :ikoni "paallystys_vesilammikko"
    :avain :vesilammikot
    :vaatii-nappaimiston? false}
   {:nimi "Epä\u00ADtasai\u00ADset reunat"
    :tyyppi :vali
    :ikoni "paallystys_epatasaiset_reunat"
    :avain :epatasaisetreunat
    :vaatii-nappaimiston? false}
   {:nimi "Jyrän jälkiä"
    :tyyppi :vali
    :ikoni "paallystys_jyran_jalki"
    :avain :jyranjalkia
    :vaatii-nappaimiston? false}
   {:nimi "Side\u00ADaine\u00ADläikkiä"
    :tyyppi :vali
    :ikoni "paallystys_laikka"
    :avain :sideainelaikkia
    :vaatii-nappaimiston? false}
   {:nimi "Vää\u00ADrä korkeu\u00ADsasema"
    :tyyppi :vali
    :ikoni "paallystys_vaara_korkeus"
    :avain :vaarakorkeusasema
    :vaatii-nappaimiston? false}
   {:nimi "Pinta harva"
    :tyyppi :vali
    :ikoni "paallystys_harva_pinta"
    :avain :pintaharva
    :vaatii-nappaimiston? false}
   {:nimi "Pinta\u00ADkuivatus puut\u00ADteel\u00ADlinen"
    :tyyppi :vali
    :ikoni "paallystys_pintakuivatus_puute"
    :avain :pintakuivatuspuute
    :vaatii-nappaimiston? false}
   {:nimi "Kai\u00ADvojen korkeu\u00ADsasema"
    :tyyppi :vali
    :ikoni "paallystys_kaivon_korkeus"
    :avain :kaivojenkorkeusasema
    :vaatii-nappaimiston? false}])

(def oletusvalilehdet
  [{:avain :talvihoito
    :nimi "Talviset pinnat"
    :sisalto valilehti-talviset-pinnat}
   {:avain :liikennemerkit
    :nimi "Liikennemerkit"
    :sisalto valilehti-liikennemerkit}
   {:avain :viherhoito
    :nimi "Viherhoito"
    :sisalto valilehti-viherhoito}
   {:avain :reunat
    :nimi "Reunat"
    :sisalto valilehti-reunat}
   {:avain :p-ja-l-alueet
    :nimi "P- ja L-alueet"
    :sisalto valilehti-p-ja-l-alueet}
   {:avain :soratiet
    :nimi "Soratiet"
    :sisalto valilehti-soratiet}
   {:avain :muut
    :nimi "Sillat"
    :sisalto valilehti-sillat}
   {:avain :paallystys
    :nimi "Päällystys"
    :sisalto valilehti-paallystys}])

(defn pistemainen-havainto-painettu! [{:keys [nimi avain] :as tiedot}]
  (.log js/console "Kirjataan pistemäinen havainto: " (pr-str avain))
  (ilmoitukset/ilmoita
    (str "Pistemäinen havainto kirjattu: " nimi))
  (reitintallennus/kirjaa-kertakirjaus
    @s/idxdb
    {:sijainti (select-keys (:nykyinen @s/sijainti) [:lat :lon])
     :aikaleima (tc/to-long (lt/local-now))
     :tarkastusajo @s/tarkastusajo-id
     :havainnot (into #{} (remove nil? (conj @s/jatkuvat-havainnot avain)))
     :mittaukset {}}))

(defn valikohtainen-havainto-painettu!
  "Asettaa välikohtaisen havainnon päälle tai pois päältä."
  [{:keys [avain vaatii-nappaimiston? mittaus] :as tiedot}]
  ;; Jatkuva havainto ensin päälle
  (s/togglaa-jatkuva-havainto! avain)
  (.log js/console (pr-str "Välikohtaiset havainnot nyt : " @s/jatkuvat-havainnot))

  ;; Mittaus päälle jos tarvii
  (when (and vaatii-nappaimiston?
             (avain @s/jatkuvat-havainnot))
    (s/aseta-mittaus-paalle! (:tyyppi mittaus)))

  ;; Mittaus pois jos tarvii
  (when (and vaatii-nappaimiston?
             (not (avain @s/jatkuvat-havainnot)))
    (s/aseta-mittaus-pois!))

  ;; Tee merkintä
  (reitintallennus/tallenna-sovelluksen-tilasta-merkinta-indexeddbn!))