(ns harja.palvelin.palvelut.hallinta.urakkahenkilot
  (:require [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.excel-vienti :as excel-vienti]
            [harja.palvelin.raportointi.excel :as excel]
            [taoensso.timbre :as log]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelut]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.kyselyt.yhteyshenkilot :as yhteyshenkilot-q]
            [clojure.string :as str]))

(defn- kayttajan-rooli-str
  "Palauttaa vastuuhenkilön/urakanvalvojan roolin ihmisluettavassa muodossa"
  [{:keys [rooli ensisijainen toissijainen-varahenkilo]}]
  (str/join
    " "
    (keep identity
      [(case rooli
         ("ELY_Urakanvalvoja" "Tilaajan_Urakanvalvoja") "Urakanvalvoja"
         "vastuuhenkilo" "Vastuuhenkilö")
       (cond
         ensisijainen nil
         toissijainen-varahenkilo "(Toissijainen varahenkilö)"
         :else "(Varahenkilö)")])))

(defn hae-urakkahenkilot [db kayttaja {:keys [urakkatyyppi paattyneet?]}]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-urakkahenkilot kayttaja)
  (map (fn [{:keys [urakkanimi etunimi sukunimi puhelin sahkoposti] :as kayttaja}]
         {:urakka urakkanimi
          :nimi (str/join " " [etunimi sukunimi])
          :puhelin puhelin
          :sahkoposti sahkoposti
          :rooli (kayttajan-rooli-str kayttaja)})
    (yhteyshenkilot-q/hae-vastuuhenkilot-hallinta db {:urakkatyyppi (name urakkatyyppi)
                                                      :paattyneet (boolean paattyneet?)})))

(defn vie-urakkahenkilot-exceliin [db workbook kayttaja tiedot]
  (let [urakkahenkilot (hae-urakkahenkilot db kayttaja tiedot)
        optiot {:nimi "Urakkahenkilöt"
                :tyhja (when (empty? urakkahenkilot) "Ei urakkahenkilöitä hakuehdoilla")}
        sarakkeet [{:otsikko "Urakka"}
                   {:otsikko "Nimi"}
                   {:otsikko "Puhelinnumero"}
                   {:otsikko "Sähköposti"}
                   {:otsikko "Rooli"}]
        rivit (map (fn [{:keys [urakka nimi puhelin sahkoposti rooli]}]
                     {:rivi
                      [urakka nimi puhelin sahkoposti rooli]}) urakkahenkilot)
        taulukot [[:taulukko optiot sarakkeet rivit]]
        taulukko (vec (concat [:raportti
                               {:nimi "Urakkahenkilöt"
                                :raportin-yleiset-tiedot {:raportin-nimi "Urakkahenkilöt"}
                                :orientaatio :landscape}]
                        taulukot))]
    (excel/muodosta-excel taulukko workbook)))

(defrecord UrakkaHenkilotHallinta []
  component/Lifecycle
  (start [{:keys [http-palvelin db excel-vienti] :as this}]
    (julkaise-palvelu http-palvelin :hae-urakkahenkilot
      (fn [kayttaja tiedot]
        (hae-urakkahenkilot db kayttaja tiedot)))
    (when excel-vienti
      (excel-vienti/rekisteroi-excel-kasittelija! excel-vienti :hae-urakkahenkilot-exceliin
        (partial #'vie-urakkahenkilot-exceliin db)))
    this)
  (stop [{:keys [http-palvelin] :as this}]
    (poista-palvelut http-palvelin
      :hae-urakkahenkilot)
    this))
