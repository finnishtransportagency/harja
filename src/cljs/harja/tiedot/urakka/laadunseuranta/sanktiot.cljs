(ns harja.tiedot.urakka.laadunseuranta.sanktiot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]

            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.domain.urakka :as u-domain])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def nakymassa? (atom false))
(defn uusi-sanktio [urakkatyyppi]
  {:suorasanktio true
   :laji (cond
           (= :hoito urakkatyyppi) :A
           (u-domain/vesivaylaurakkatyyppi? urakkatyyppi) :vesivayla_sakko
           ;; Luultavasti ylläpidon urakka
           :default :yllapidon_sakko)
   :toimenpideinstanssi (when (= 1 (count @urakka/urakan-toimenpideinstanssit))
                          (:tpi_id (first @urakka/urakan-toimenpideinstanssit)))
   :laatupoikkeama {:tekijanimi @istunto/kayttajan-nimi
                    :paatos {:paatos "sanktio"}}})

(defonce valittu-sanktio (atom nil))

(defn hae-urakan-sanktiot
  "Hakee urakan sanktiot annetulle hoitokaudelle."
  [{:keys [urakka-id alku loppu vain-yllapitokohteettomat?]}]
  (k/post! :hae-urakan-sanktiot {:urakka-id urakka-id
                                 :alku      alku
                                 :loppu     loppu
                                 :vain-yllapitokohteettomat? vain-yllapitokohteettomat?}))

(defonce haetut-sanktiot
  (reaction<! [urakka (:id @nav/valittu-urakka)
               hoitokausi @urakka/valittu-hoitokausi
               _ @nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when @nakymassa?
                (hae-urakan-sanktiot {:urakka-id urakka
                                      :alku (first hoitokausi)
                                      :loppu (second hoitokausi)}))))

(defn kasaa-tallennuksen-parametrit
  [s urakka-id]
  {:sanktio        (dissoc s :laatupoikkeama :yllapitokohde)
   :laatupoikkeama (assoc (:laatupoikkeama s) :urakka urakka-id
                                              :yllapitokohde (:id (:yllapitokohde s)))
   :hoitokausi     @urakka/valittu-hoitokausi})

(defn tallenna-sanktio
  [sanktio urakka-id]
  (go
    (let [sanktiot-tallennuksen-jalkeen
          (<! (k/post! :tallenna-suorasanktio (kasaa-tallennuksen-parametrit sanktio urakka-id)))]
     (reset! haetut-sanktiot sanktiot-tallennuksen-jalkeen))))

(defn sanktion-tallennus-onnistui
  [palautettu-id sanktio]
  (when (and
          palautettu-id
          (pvm/valissa?
            (get-in sanktio [:laatupoikkeama :aika])
            (first @urakka/valittu-hoitokausi)
            (second @urakka/valittu-hoitokausi)))
    (if (some #(= (:id %) palautettu-id) @haetut-sanktiot)
     (reset! haetut-sanktiot
             (into [] (map (fn [vanha] (if (= palautettu-id (:id vanha)) (assoc sanktio :id palautettu-id) vanha)) @haetut-sanktiot)))

     (reset! haetut-sanktiot
             (into [] (concat @haetut-sanktiot [(assoc sanktio :id palautettu-id)]))))))


(defonce sanktiotyypit
  (reaction<! [laadunseurannassa? @laadunseuranta/laadunseurannassa?]
              (when laadunseurannassa?
                (k/get! :hae-sanktiotyypit))))

(defn lajin-sanktiotyypit
  [laji]
  (filter #((:laji %) laji) @sanktiotyypit))
