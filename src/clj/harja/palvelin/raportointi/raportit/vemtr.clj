(ns harja.palvelin.raportointi.raportit.vemtr
  "Valtakunnallinen ja ELY-kohtainen määrätoteumaraportti"
  (:require [harja.kyselyt
             [vemtr :as vemtr-q]
             [tehtavamaarat :as tm-q]]
            [harja.palvelin.raportointi.raportit.tehtavamaarat :as tm-r]
            [harja.pvm :as pvm])
  (:import (java.math RoundingMode)))


(defn hae-tm-combo [db params]  
  (let [combo-fn 
        (juxt tm-q/hae-tehtavamaarat-ja-toteumat-aikavalilla
              vemtr-q/hae-yh-suunnitellut-ja-toteutuneet-aikavalilla
              #_vemtr-q/hae-yh-toteutuneet-tehtavamaarat-ja-toteumat-aikavalilla
             #_vemtr-q/hae-yh-suunnitellut-tehtavamaarat-ja-toteumat-aikavalilla)
        kolme-tulosjoukkoa (combo-fn db params)]
    (apply concat kolme-tulosjoukkoa)))

(defn suorita
  [db user params]
  (let [{:keys [otsikot rivit debug]} (tm-r/muodosta-taulukko db user hae-tm-combo params)]
    [:raportti
     {:nimi "Tehtävämäärät"}
     [:taulukko
      {:otsikko    "Määrätoteumat ajalta "
       :sheet-nimi "Määrätoteumat"}
      otsikot
      rivit]]))

;; tarvittavat sarakkeet:
;;   - 1 nimi (esim sorateiden pölynsidonta) [x]
;;   - 2 jarjestys (tpi:n tiedoista generoitu järjestysindeksi, käytetään sorttaukseen mutta ei näy taulukossa)
;;   - 3 suunniteltu (suunniteltu määrä, esim 100)
;;   - 4 hoitokauden-alkuvuosi (esim 2020)
;;   - 5 suunnitteluyksikko (esim km)
;;   - 6 yksikko
;;   - 7 toimenpidekoodi (tpk-taulun tason 4 id eli tehtävä, jossa suunnitteluyksikkö, hinnoittelutyyppi, muokkaus- ja voimassaolotietoja ym)
;;    - 8 urakka (urakka-id)
;;    - 9 toimenpide, tpk-taulun tason 3 rivi, esim Oulu MHU Soratien hoito TP (toimenpideinstanssi-taulusta nimi, siellä urakkakohtaisia toimenpiteiden laskutustietoja )
;;    - 10 toteuma - tulee toteuma_tehtava tai toteuma_materiaali -taulukon linkityksen kautta
;; (11 kpl)
;;    - 11 toteutunut-materiaalimaara
;;    - 12 rivityyppi (yh-toteutuneet, yh-suunnitellut, puuttuu = mh)
