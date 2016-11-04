(ns harja-laadunseuranta.tiedot.paatason-navigointi)

(def oletusvalilehdet
  [{:avain :talvihoito
    :nimi "Talvihoito"
    :sisalto [{:nimi "Liu\u00ADkas\u00ADta"
               :ikoni ""
               :tyyppi :vali
               :vaatii-nappaimiston? true}
              {:nimi "Lu\u00ADmis\u00ADta"
               :ikoni ""
               :tyyppi :vali
               :vaatii-nappaimiston? true}
              {:nimi "Tasaus\u00ADpuute"
               :ikoni ""
               :tyyppi :vali
               :vaatii-nappaimiston? true}
              {:nimi "Py\u00ADsäkki: epä\u00ADtasainen polanne"
               :ikoni ""
               :tyyppi :piste
               :vaatii-nappaimiston? false}
              {:nimi "Py\u00ADsäkki auraa\u00ADmatta"
               :ikoni ""
               :tyyppi :piste
               :vaatii-nappaimiston? false}
              {:nimi "Py\u00ADsäkki hiekoit\u00ADtamatta"
               :ikoni ""
               :tyyppi :piste
               :vaatii-nappaimiston? false}
              {:nimi "Liikenne\u00ADmerkki likai\u00ADnen"
               :ikoni ""
               :tyyppi :piste
               :vaatii-nappaimiston? false}
              {:nimi "Liikenne\u00ADmerkki lumi\u00ADnen"
               :ikoni ""
               :tyyppi :piste
               :vaatii-nappaimiston? false}]}
   {:avain :liikenneympariston-hoito
    :nimi "Liikenneympäristön hoito"
    :sisalto [{:nimi "Liikenne\u00ADmerkki likai\u00ADnen"
               :ikoni ""
               :tyyppi :piste
               :vaatii-nappaimiston? false}
              {:nimi "Liikenne\u00ADmerkki vinos\u00ADsa"
               :ikoni ""
               :tyyppi :piste
               :vaatii-nappaimiston? false}]}])