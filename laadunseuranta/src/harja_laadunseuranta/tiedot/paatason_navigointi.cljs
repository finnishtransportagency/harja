(ns harja-laadunseuranta.tiedot.paatason-navigointi)

(def valilehti-talviset-pinnat
  [{:nimi "Liu\u00ADkas\u00ADta"
    :ikoni nil
    :tyyppi :vali
    :vaatii-nappaimiston? true}
   {:nimi "Lu\u00ADmis\u00ADta"
    :ikoni nil
    :tyyppi :vali
    :vaatii-nappaimiston? true}
   {:nimi "Tasaus\u00ADpuute"
    :ikoni nil
    :tyyppi :vali
    :vaatii-nappaimiston? true}
   {:nimi "Py\u00ADsäkki: epä\u00ADtasainen polanne"
    :ikoni nil
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Py\u00ADsäkki auraa\u00ADmatta"
    :ikoni nil
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Py\u00ADsäkki hiekoit\u00ADtamatta"
    :ikoni nil
    :tyyppi :piste
    :vaatii-nappaimiston? false}])

(def valilehti-p-ja-l-alueet
  [{:nimi "Auraa\u00ADmatta"
    :ikoni nil
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Hiekoit\u00ADtamatta"
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Epä\u00ADtasainen polanne"
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Puhdis\u00ADtet\u00ADtava"
    :tyyppi :piste
    :ikoni "p_alue_puhdistettava"
    :vaatii-nappaimiston? false}
   {:nimi "Korjat\u00ADtavaa"
    :tyyppi :piste
    :ikoni "p_alue_korjattavaa"
    :vaatii-nappaimiston? false}
   {:nimi "Viher\u00ADalueet hoita\u00ADmatta"
    :tyyppi :piste
    :ikoni "p_alue_viheralue"
    :vaatii-nappaimiston? false}])

(def valilehti-reunat
  [{:nimi "Reuna\u00ADpaalu likai\u00ADnen"
    :tyyppi :piste
    :ikoni "reunapaalu_likainen"
    :vaatii-nappaimiston? false}
   {:nimi "Reuna\u00ADpaalu vino\u00ADssa"
    :tyyppi :piste
    :ikoni "reunapaalu_vinossa"
    :vaatii-nappaimiston? false}
   {:nimi "Rumpu tukossa"
    :tyyppi :piste
    :ikoni "rumpu_tukossa"
    :vaatii-nappaimiston? false}
   {:nimi "Rumpu liettynyt"
    :tyyppi :piste
    :ikoni "rumpu_liettynyt"
    :vaatii-nappaimiston? false}
   {:nimi "Rumpi rikki"
    :tyyppi :piste
    :ikoni "rumpu_rikki"
    :vaatii-nappaimiston? false}
   {:nimi "Kaide\u00ADvaurio"
    :tyyppi :piste
    :ikoni "kaidevaurio"
    :vaatii-nappaimiston? false}
   {:nimi "Kiveys\u00ADvaurio"
    :tyyppi :piste
    :ikoni "kiveysvaurio"
    :vaatii-nappaimiston? false}])

(def valilehti-viherhoito
  [{:nimi "Vesakko raivaa\u00ADmatta"
    :ikoni nil
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Niit\u00ADtämättä"
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Näkemä\u00ADalue raivaa\u00ADmatta"
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Liiken\u00ADnetila hoita\u00ADmatta"
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Istu\u00ADtukset hoita\u00ADmatta"
    :tyyppi :piste
    :vaatii-nappaimiston? false}])

(def valilehti-soratien-hoito
  [{:nimi "Sora\u00ADtie"
    :ikoni nil
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Soran\u00ADpienta\u00ADreet: Reuna\u00ADpaletta"
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Soran\u00ADpienta\u00ADreet: Reuna\u00ADtäyttö puutteel\u00ADlinen"
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Soran\u00ADpienta\u00ADreet: Puutteel\u00ADlinen liuska\u00ADvaurio"
    :tyyppi :piste
    :vaatii-nappaimiston? false}])

(def valilehti-muut
  [{:nimi "Liikenne\u00ADmerkki likai\u00ADnen"
    :ikoni "liikennemerkki_likainen"
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Liikenne\u00ADmerkki vino\u00ADssa"
    :ikoni "liikennemerkki_vinossa"
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Liikenne\u00ADmerkki lumi\u00ADnen"
    :ikoni "liikennemerkki_luminen"
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Oja: tukossa"
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Oja: Ylijäämä\u00ADmassa tasattu huonosti"
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Oja: Kiviä poista\u00ADmatta"
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Silta: Puhdista\u00ADmatta"
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Silta: Vaurioita"
    :tyyppi :piste
    :vaatii-nappaimiston? false}
   {:nimi "Silta: Saumoissa puutteita"
    :tyyppi :piste
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