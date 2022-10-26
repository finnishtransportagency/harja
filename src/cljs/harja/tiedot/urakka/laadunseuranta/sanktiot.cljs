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
  (:require-macros [harja.atom :refer [reaction<! reaction-writable]]
                   [cljs.core.async.macros :refer [go]]))

(def nakymassa? (atom false))
(defn uusi-sanktio [urakkatyyppi]
  {:suorasanktio true
   :laji (cond
           (#{:hoito :teiden-hoito} urakkatyyppi) :A
           (u-domain/vesivaylaurakkatyyppi? urakkatyyppi) :vesivayla_sakko
           :else :yllapidon_sakko)
   :toimenpideinstanssi (when (= 1 (count @urakka/urakan-toimenpideinstanssit))
                          (:tpi_id (first @urakka/urakan-toimenpideinstanssit)))
   :laatupoikkeama {:tekijanimi @istunto/kayttajan-nimi
                    :paatos {:paatos "sanktio"}}})

(defonce valittu-sanktio (atom nil))

(defn hae-urakan-sanktiot-ja-bonukset
  "Hakee urakan sanktiot ja bonukset annetulle hoitokaudelle.
  Kohderajapinta palauttaa oletuksena sekä sanktiot, että bonukset. Tarvittaessa sanktiot tai bonukset haun
  voi estää :hae-sanktiot? false tai :hae-bonukset? false optiolla."
  [{:keys [urakka-id alku loppu vain-yllapitokohteettomat? hae-sanktiot? hae-bonukset?]}]
  (k/post! :hae-urakan-sanktiot-ja-bonukset {:urakka-id urakka-id
                                             :alku      alku
                                             :loppu     loppu
                                             :vain-yllapitokohteettomat? vain-yllapitokohteettomat?
                                             :hae-sanktiot? hae-sanktiot?
                                             :hae-bonukset? hae-bonukset?}))

(defonce haetut-sanktiot-ja-bonukset
  (reaction<! [urakka (:id @nav/valittu-urakka)
               hoitokausi @urakka/valittu-hoitokausi
               [kk-alku kk-loppu] @urakka/valittu-hoitokauden-kuukausi
               _ @nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when @nakymassa?
                (hae-urakan-sanktiot-ja-bonukset {:urakka-id urakka
                                                  :alku (or kk-alku (first hoitokausi))
                                                  :loppu (or kk-loppu (second hoitokausi))}))))

(defn hae-sanktion-liitteet!
  "Hakee sanktion liitteet urakan id:n ja sanktioon tietomallissa liittyvän laatupoikkeaman id:n
  perusteella."
  [urakka-id laatupoikkeama-id sanktio-atom]
  (log "hae-sanktion-liitteet!  " (pr-str urakka-id ) " laatupoikkeama-id " (pr-str laatupoikkeama-id))
  (go (let [vastaus (<! (k/post! :hae-sanktion-liitteet {:urakka-id urakka-id
                                                         :laatupoikkeama-id laatupoikkeama-id}))]
        (if (k/virhe? vastaus)
          :virhe
          (swap! sanktio-atom (fn [] (assoc-in @sanktio-atom [:laatupoikkeama :liitteet] vastaus)))))))

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
      (reset! valittu-sanktio nil)
      (reset! haetut-sanktiot-ja-bonukset sanktiot-tallennuksen-jalkeen))))

(defn sanktion-tallennus-onnistui
  [palautettu-id sanktio]
  (when (and
          palautettu-id
          (pvm/valissa?
            (get-in sanktio [:laatupoikkeama :aika])
            (first @urakka/valittu-hoitokausi)
            (second @urakka/valittu-hoitokausi)))
    (if (some #(= (:id %) palautettu-id) @haetut-sanktiot-ja-bonukset)
      (reset! haetut-sanktiot-ja-bonukset
             (into [] (map (fn [vanha] (if (= palautettu-id (:id vanha)) (assoc sanktio :id palautettu-id) vanha)) @haetut-sanktiot-ja-bonukset)))

      (reset! haetut-sanktiot-ja-bonukset
             (into [] (concat @haetut-sanktiot-ja-bonukset [(assoc sanktio :id palautettu-id)]))))))


(defonce sanktiotyypit
  (reaction<! [laadunseurannassa? @laadunseuranta/laadunseurannassa?]
              (when laadunseurannassa?
                (k/get! :hae-sanktiotyypit))))
