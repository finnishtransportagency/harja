(ns harja.tiedot.urakka.laadunseuranta.sanktiot
  (:require [reagent.core :refer [atom]]
            [reagent.ratom :refer [reaction]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]

            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.domain.urakka :as u-domain]
            [harja.ui.viesti :as viesti])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(def nakymassa? (atom false))

(defn uusi-sanktio [urakkatyyppi]
  (let [nyt (pvm/nyt)
        default-perintapvm (pvm/luo-pvm-dec-kk (pvm/vuosi nyt) (pvm/kuukausi nyt) 15)]
    {:suorasanktio true
     :laji (cond
             (u-domain/mh-tai-hoitourakka? urakkatyyppi) :A
             (u-domain/vesivaylaurakkatyyppi? urakkatyyppi) :vesivayla_sakko
             :else :yllapidon_sakko)
     :perintapvm default-perintapvm
     :toimenpideinstanssi (when (= 1 (count @urakka/urakan-toimenpideinstanssit))
                            (:tpi_id (first @urakka/urakan-toimenpideinstanssit)))
     :laatupoikkeama {:tekijanimi @istunto/kayttajan-nimi
                      :paatos {:paatos "sanktio"
                               :kasittelyaika nyt}}}))

(defn pyorayta-laskutuskuukausi-valinnat
  []
  (let [{:keys [alkupvm loppupvm]} @nav/valittu-urakka
        vuodet (range (pvm/vuosi alkupvm) (pvm/vuosi loppupvm))]
    (into []
      (sort-by (juxt :vuosi :kuukausi)
        (mapcat (fn [vuosi]
                  (let [kuukaudet (range 1 13)
                        inc-jos-tarvii (fn [kuukausi vuosi]
                                         (if (< kuukausi 10)
                                           (inc vuosi)
                                           vuosi))]
                    (into [] (map
                               (fn [kuukausi]
                                 {:pvm (pvm/->pvm (str "15." kuukausi "." (inc-jos-tarvii kuukausi vuosi)))
                                  :vuosi (inc-jos-tarvii kuukausi vuosi)
                                  :kuukausi kuukausi
                                  :teksti (str (pvm/kuukauden-nimi kuukausi true)
                                            " " (inc-jos-tarvii kuukausi vuosi)
                                            " (" (pvm/paivamaara->mhu-hoitovuosi-nro
                                                   alkupvm
                                                   (pvm/->pvm (str "15." kuukausi "." (inc-jos-tarvii kuukausi vuosi))))
                                            ". hoitovuosi)")}))
                      kuukaudet)))
          vuodet)))))

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

(def paivita-sanktiot-ja-bonukset-atom (atom false))
(defonce haetut-sanktiot-ja-bonukset
  (reaction<! [urakka (:id @nav/valittu-urakka)
               hoitokausi @urakka/valittu-hoitokausi
               [kk-alku kk-loppu] @urakka/valittu-hoitokauden-kuukausi
               _ @nakymassa?
               _ @paivita-sanktiot-ja-bonukset-atom]
              {:nil-kun-haku-kaynnissa? true}
              (when @nakymassa?
                (hae-urakan-sanktiot-ja-bonukset {:urakka-id urakka
                                                  :alku (or kk-alku (first hoitokausi))
                                                  :loppu (or kk-loppu (second hoitokausi))}))))

(defn paivita-sanktiot-ja-bonukset!
  "Vaihtaa paivita-sanktiot-ja-bonukset atomin arvon, joka käynnistää sanktioiden ja bonusten haun."
  []
  (swap! paivita-sanktiot-ja-bonukset-atom not))

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

(def lajisuodatin-tiedot
  {:muistutukset {:teksti "Muistutukset" :jarjestys 1}
   :sanktiot {:teksti "Sanktiot" :jarjestys 2}
   :bonukset {:teksti "Bonukset" :jarjestys 3}
   :arvonvahennykset {:teksti "Arvonvähennykset" :jarjestys 4}})

(defn- jarjesta-suodattimet [s1 s2]
  (let [s1-idx (:jarjestys (lajisuodatin-tiedot s1))
        s2-idx (:jarjestys (lajisuodatin-tiedot s2))]
    (< s1-idx s2-idx)))

(def sanktio-bonus-suodattimet-oletusarvo
  (set (keys lajisuodatin-tiedot)))

(def sanktio-bonus-suodattimet
  (atom sanktio-bonus-suodattimet-oletusarvo))

(def urakan-lajisuodattimet
  (reaction
    ;; Urakan vaihtuessa nollataan suodattimet
    (reset! sanktio-bonus-suodattimet sanktio-bonus-suodattimet-oletusarvo)
    ;; Järjestetään setti
    (apply sorted-set-by
      jarjesta-suodattimet
      (cond
        (u-domain/yllapitourakka? (:tyyppi @nav/valittu-urakka))
        (disj sanktio-bonus-suodattimet-oletusarvo :arvonvahennykset)

        :else sanktio-bonus-suodattimet-oletusarvo))))

(defn kasaa-tallennuksen-parametrit
  [s urakka-id]
  {:sanktio        (dissoc s :laatupoikkeama :yllapitokohde)
   :laatupoikkeama (assoc (:laatupoikkeama s) :urakka urakka-id
                                              :yllapitokohde (:id (:yllapitokohde s)))
   :hoitokausi     @urakka/valittu-hoitokausi})

(defn tallenna-sanktio
  [sanktio urakka-id onnistui-fn]
  (go
    (let [vastaus (<! (k/post! :tallenna-suorasanktio (kasaa-tallennuksen-parametrit sanktio urakka-id)))]
      (if (k/virhe? vastaus)
        (viesti/nayta-toast! "Sanktion tallennus epäonnistui!" :varoitus)
        (do
          (viesti/nayta-toast! "Sanktion tallennus onnistui" :onnistui)
          (reset! valittu-sanktio nil)
          ;; Haetaan onnistuneen tallennuksen jälkeen uusiksi sanktiot & bonukset listan tiedot
          (paivita-sanktiot-ja-bonukset!)
          (when (fn? onnistui-fn) (onnistui-fn))))
      vastaus)))

(defn poista-suorasanktio
  [sanktion-id urakka-id onnistui-fn]
  (go
    (let [payload {:id sanktion-id
                   :urakka-id urakka-id}
          vastaus (<! (k/post! :poista-suorasanktio payload))]

      (if (k/virhe? vastaus)
        (viesti/nayta-toast! "Sanktion poisto epäonnistui!" :varoitus)
        (do
          (viesti/nayta-toast! "Sanktio poistettu" :onnistui)
          (reset! valittu-sanktio nil)
          ;; Haetaan onnistuneen poiston jälkeen uusiksi sanktiot & bonukset listan tiedot
          (paivita-sanktiot-ja-bonukset!)
          (when (fn? onnistui-fn) (onnistui-fn))))
      vastaus)))

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

(defn- bonus? [rivi]
  (boolean (:bonus? rivi)))

(defn- sanktio? [rivi]
  (not (:bonus? rivi)))

(defn- arvonvahennys? [rivi]
  (= :arvonvahennyssanktio (:laji rivi)))

(defn- muistutus? [rivi]
  (= :muistutus (:laji rivi)))

(defn- rivin-tyyppi [rivi]
  (cond
    (muistutus? rivi) :muistutukset
    (bonus? rivi) :bonukset
    (sanktio? rivi) :sanktiot
    (arvonvahennys? rivi) :arvonvahennykset))

(defn suodata-sanktiot-ja-bonukset [sanktiot-ja-bonukset]
  (let [kaikki @urakan-lajisuodattimet
        valitut @sanktio-bonus-suodattimet]
    (if (= kaikki valitut)
      ;; Kaikki suodattimet valittu, ei suodateta mitään pois.
      sanktiot-ja-bonukset
      (filter
        #(valitut (rivin-tyyppi %))
        sanktiot-ja-bonukset))))
