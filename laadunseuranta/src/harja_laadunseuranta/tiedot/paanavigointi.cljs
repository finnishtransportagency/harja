(ns harja-laadunseuranta.tiedot.paanavigointi
  (:require [harja-laadunseuranta.tiedot.sovellus :as s]
            [harja-laadunseuranta.tiedot.ilmoitukset :as ilmoitukset]
            [harja-laadunseuranta.tiedot.reitintallennus :as reitintallennus]
            [harja-laadunseuranta.tiedot.nappaimisto :as nappaimisto]
            [cljs-time.local :as l]
            [harja-laadunseuranta.utils :as utils]
            [cljs-time.coerce :as tc]
            [cljs-time.local :as lt]))

(def valilehti-talviset-pinnat
  [{:nimi "Liu\u00ADkas\u00ADta"
    :ikoni "liukas-36"
    :ikoni-lahde "livicons"
    :tyyppi :vali
    :avain :liukasta
    :mittaus {:nimi "Liukkaus"
              :tyyppi :kitkamittaus
              :yksikko nil}
    :vaatii-nappaimiston? true}
   {:nimi "Lu\u00ADmis\u00ADta"
    :ikoni "lumi-36"
    :ikoni-lahde "livicons"
    :tyyppi :vali
    :avain :lumista
    :mittaus {:nimi "Lumisuus"
              :tyyppi :lumisuus
              :yksikko "cm"}
    :vaatii-nappaimiston? true}
   {:nimi "Tasaus\u00ADpuute"
    :avain :tasauspuute
    :tyyppi :vali
    :ikoni "epatasa-36"
    :ikoni-lahde "livicons"
    :mittaus {:nimi "Tasauspuute"
              :tyyppi :talvihoito-tasaisuus
              :yksikko "cm"}
    :vaatii-nappaimiston? true}
   {:nimi "P: epä\u00ADtasainen polanne"
    :tyyppi :piste
    :avain :pysakilla-epatasainen-polanne
    :ikoni "polanne-36"
    :ikoni-lahde "livicons"
    :vaatii-nappaimiston? false}
   {:nimi "P: auraa\u00ADmatta"
    :tyyppi :piste
    :avain :pysakki-auraamatta
    :ikoni "pinta-lumi-36"
    :ikoni-lahde "livicons"
    :vaatii-nappaimiston? false}
   {:nimi "P: hiekoit\u00ADtamatta"
    :tyyppi :piste
    :avain :pysakki-hiekoittamatta
    :ikoni "pinta-hiekka-kielto-36"
    :ikoni-lahde "livicons"
    :vaatii-nappaimiston? false}])

(def valilehti-liikennemerkit
  [{:nimi "Liikenne\u00ADmerkki likai\u00ADnen"
    :ikoni "liikennemerkki-lika-36"
    :ikoni-lahde "livicons"
    :tyyppi :piste
    :avain :liikennemerkki-likainen
    :vaatii-nappaimiston? false}
   {:nimi "Liikenne\u00ADmerkki vino\u00ADssa"
    :ikoni "liikennemerkki-vino-36"
    :ikoni-lahde "livicons"
    :tyyppi :piste
    :avain :liikennemerkki-vinossa
    :vaatii-nappaimiston? false}
   {:nimi "Liikenne\u00ADmerkki vauri\u00ADoitunut"
    :ikoni "liikennemerkki_vaurioitunut"
    :tyyppi :piste
    :avain :liikennemerkki-vaurioitunut
    :vaatii-nappaimiston? false}
   {:nimi "Liikenne\u00ADmerkki lumi\u00ADnen"
    :ikoni "liikennemerkki-lumi-36"
    :ikoni-lahde "livicons"
    :tyyppi :piste
    :avain :liikennemerkki-luminen
    :vaatii-nappaimiston? false}])

(def valilehti-viherhoito
  [{:nimi "Vesakko raivaa\u00ADmatta"
    :tyyppi :vali
    :ikoni "vesakko-leikkaus-36"
    :ikoni-lahde "livicons"
    :avain :vesakko-raivaamatta
    :vaatii-nappaimiston? false}
   {:nimi "Niit\u00ADtämättä"
    :tyyppi :vali
    :avain :niittamatta
    :ikoni "heina-leikkaus-36"
    :ikoni-lahde "livicons"
    :vaatii-nappaimiston? false}
   {:nimi "Näkemä\u00ADalue raivaa\u00ADmatta"
    :tyyppi :piste
    :avain :nakemaalue-raivaamatta
    :ikoni "heina-silma-36"
    :ikoni-lahde "livicons"
    :vaatii-nappaimiston? false}
   {:nimi "Liiken\u00ADnetila hoita\u00ADmatta"
    :tyyppi :piste
    :avain :liikennetila-hoitamatta
    :ikoni "kukka-kuihtunut-36"
    :ikoni-lahde "livicons"
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
   {:nimi "Reuna\u00ADpaalu vauri\u00ADoitunut"
    :tyyppi :piste
    :avain :reunapaalut-vaurioitunut
    :ikoni "reunapaalu_vaurioitunut"
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
  [{:nimi "Sora\u00ADtie\u00ADtarkas\u00ADtus"
    :tyyppi :vali
    :ikoni "soratie_alkaa"
    :avain :soratie
    :mittaus {:nimi "Soratie"
              :tyyppi :soratie
              :yksikko nil}
    :vaatii-nappaimiston? true}
   {:nimi "Maa\u00ADkivi"
    :tyyppi :piste
    :ikoni "maakivi"
    :avain :maakivi
    :vaatii-nappaimiston? false}])

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

(defn pistemainen-havainto-painettu! [{:keys [nimi avain] :as havainto}]
  (.log js/console "Kirjataan pistemäinen havainto: " (pr-str avain))
  (ilmoitukset/ilmoita
    (str "Pistemäinen havainto kirjattu: " nimi)
    s/ilmoitus)
  (reitintallennus/kirjaa-pistemainen-havainto!
    {:idxdb @s/idxdb
     :sijainti s/sijainti
     :tarkastusajo-id s/tarkastusajo-id
     :jatkuvat-havainnot s/jatkuvat-havainnot
     :havainto-avain avain
     :epaonnistui-fn reitintallennus/merkinta-epaonnistui}))

(defn valikohtainen-havainto-painettu!
  "Asettaa välikohtaisen havainnon päälle tai pois päältä."
  [{:keys [nimi avain vaatii-nappaimiston? mittaus] :as havainto}]
  ;; Jatkuva havainto ensin päälle
  (s/togglaa-jatkuva-havainto! avain)
  (.log js/console (pr-str "Välikohtaiset havainnot nyt : " @s/jatkuvat-havainnot))

  ;; Ilmoitus
  (if (@s/jatkuvat-havainnot avain)
    (ilmoitukset/ilmoita
      (str (or (:nimi mittaus) nimi) " alkaa")
      s/ilmoitus)
    (ilmoitukset/ilmoita
      (str (or (:nimi mittaus) nimi) " päättyy")
      s/ilmoitus))

  ;; Mittaus päälle jos tarvii
  (when (and vaatii-nappaimiston?
             (avain @s/jatkuvat-havainnot))
    (nappaimisto/alusta-mittaussyotto! (:tyyppi mittaus) s/mittaussyotto)
    (nappaimisto/alusta-soratiemittaussyotto! s/soratiemittaussyotto)
    (s/aseta-mittaus-paalle! (:tyyppi mittaus)))

  ;; Mittaus pois jos tarvii
  (when (and vaatii-nappaimiston?
             (not (avain @s/jatkuvat-havainnot)))
    (s/aseta-mittaus-pois!))

  ;; Tee merkintä
  (reitintallennus/kirjaa-yksittainen-reittimerkinta!
    {:idxdb @s/idxdb
     :sijainti s/sijainti
     :tarkastusajo-id s/tarkastusajo-id
     :jatkuvat-havainnot s/jatkuvat-havainnot
     :mittaustyyppi s/mittaustyyppi
     :soratiemittaussyotto s/soratiemittaussyotto
     :epaonnistui-fn reitintallennus/merkinta-epaonnistui}))

(defn avaa-havaintolomake! []
  (.log js/console "Avataan havaintolomake!")
  (reset! s/havaintolomake-auki true))

(defn hampurilaisvalikko-painettu! []
  (swap! s/paanavigoinnin-hampurilaisvalikon-lista-nakyvissa? not))

(defn hampurilaisvalikon-lista-item-valittu! [avain]
  (reset! s/paanavigoinnin-hampurilaisvalikon-lista-nakyvissa? false)
  (reset! s/paanavigoinnin-valittu-valilehti avain))

(defn body-click []
  (reset! s/paanavigoinnin-hampurilaisvalikon-lista-nakyvissa? false))