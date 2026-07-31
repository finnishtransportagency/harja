(ns harja.tiedot.urakka.laadunseuranta.sanktiot
  (:require [clojure.string :as str]
            [cljs.core.async :refer [<!]]
            [reagent.core :refer [atom]]
            [reagent.ratom :refer [reaction]]

            [harja.pvm :as pvm]
            [harja.loki :refer [log]]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.urakka :as u-domain]
            [harja.tiedot.istunto :as istunto]
            [harja.domain.tierekisteri :as tr]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.domain.laadunseuranta.sanktio :as domain-sanktio])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(def nakymassa? (atom false))

(defn sanktio-konfiguraation-soveltuvuuskonteksti
  [ls-sivu vv-ls-sivu]
  (cond
    (= :laatupoikkeamat ls-sivu) :laatupoikkeama
    (or (= :sanktiot ls-sivu)
      (= :vesivayla-sanktiot vv-ls-sivu)) :urakka
    :else nil))

(defonce valitun-urakan-sanktio-konfiguraation-haku-kaynnissa? (atom false))
(defonce valitun-urakan-sanktio-konfiguraation-pyynto-id (atom 0))

(defn hae-urakan-sanktio-konfiguraatio
  [urakka-id hoitovuosi soveltuvuuskonteksti]
  (let [pyynto-id (swap! valitun-urakan-sanktio-konfiguraation-pyynto-id inc)
        vastaus-ch (k/post! :hae-urakan-sanktio-konfiguraatio
                     {:urakka-id urakka-id
                      :hoitovuosi hoitovuosi
                      :soveltuvuuskonteksti soveltuvuuskonteksti})]
    (reset! valitun-urakan-sanktio-konfiguraation-haku-kaynnissa? true)
    (go
      (<! vastaus-ch)
      (when (= pyynto-id @valitun-urakan-sanktio-konfiguraation-pyynto-id)
        (reset! valitun-urakan-sanktio-konfiguraation-haku-kaynnissa? false)))
    vastaus-ch))

(defn sanktio-konfiguraation-tila
  [sanktio-konfiguraatio haku-kaynnissa?]
  (cond
    haku-kaynnissa?
    :haku-kaynnissa

    (seq (domain-sanktio/sanktio-konfiguraation-lajit sanktio-konfiguraatio))
    :valmis

    (and (some? sanktio-konfiguraatio)
      (k/virhe? sanktio-konfiguraatio))
    :haku-epaonnistui

    :else
    :ei-konfiguraatiota))

(defn oletus-uuden-sanktion-laji
  [urakkatyyppi mahdolliset-sanktiolajit]
  (cond
    (u-domain/mh-tai-hoitourakka? urakkatyyppi)
    (first mahdolliset-sanktiolajit)

    (u-domain/vesivaylaurakkatyyppi? urakkatyyppi)
    :vesivayla_sakko

    :else
    :yllapidon_sakko))

(def valitun-urakan-sanktio-konfiguraatio
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               urakan-alkupvm (:alkupvm @nav/valittu-urakka)
               ls-sivu (nav/valittu-valilehti :laadunseuranta)
               vv-ls-sivu (nav/valittu-valilehti :laadunseuranta-vesivaylat)
               valittu-hoitokausi @urakka/valittu-hoitokausi]
    {:nil-kun-haku-kaynnissa? true}
    (let [soveltuvuuskonteksti (sanktio-konfiguraation-soveltuvuuskonteksti ls-sivu vv-ls-sivu)
          hoitovuosi (when (and urakan-alkupvm valittu-hoitokausi)
                       (pvm/hoitokausivuosi->mhu-hoitovuosi-nro urakan-alkupvm (pvm/vuosi (first valittu-hoitokausi))))]
      (when (and urakka-id soveltuvuuskonteksti hoitovuosi)
        (hae-urakan-sanktio-konfiguraatio urakka-id hoitovuosi soveltuvuuskonteksti)))))

(def valitun-urakan-sanktio-konfiguraation-tila
  (reaction
    (let [urakka-id (:id @nav/valittu-urakka)
          urakan-alkupvm (:alkupvm @nav/valittu-urakka)
          ls-sivu (nav/valittu-valilehti :laadunseuranta)
          vv-ls-sivu (nav/valittu-valilehti :laadunseuranta-vesivaylat)
          valittu-hoitokausi @urakka/valittu-hoitokausi
          soveltuvuuskonteksti (sanktio-konfiguraation-soveltuvuuskonteksti ls-sivu vv-ls-sivu)
          hoitovuosi (when (and urakan-alkupvm valittu-hoitokausi)
                       (pvm/hoitokausivuosi->mhu-hoitovuosi-nro urakan-alkupvm (pvm/vuosi (first valittu-hoitokausi))))]
      (when (and urakka-id soveltuvuuskonteksti hoitovuosi)
        (sanktio-konfiguraation-tila
          @valitun-urakan-sanktio-konfiguraatio
          @valitun-urakan-sanktio-konfiguraation-haku-kaynnissa?)))))

(defn valitun-urakan-sanktiolajin-nimi
  [laji]
  (or (domain-sanktio/sanktio-konfiguraation-lajin-nimi @valitun-urakan-sanktio-konfiguraatio laji)
    (domain-sanktio/sanktiolaji->teksti laji)))

(defn valitun-urakan-sanktiotyypit
  [laji]
  (domain-sanktio/sanktio-konfiguraation-sanktiotyypit @valitun-urakan-sanktio-konfiguraatio laji))

(def valitun-urakan-sanktiolajit
  "Valitulle urakalle mahdolliset sanktiolajit resolverin palauttamasta sanktio-konfiguraatiosta."
  (reaction
    (domain-sanktio/sanktio-konfiguraation-lajit @valitun-urakan-sanktio-konfiguraatio)))

(defn uusi-sanktio [urakkatyyppi valittu-hoitokauden-alkuvuosi]
  (let [nyt (pvm/nyt)
        default-perintapvm (pvm/luo-pvm-dec-kk valittu-hoitokauden-alkuvuosi (pvm/kuukausi nyt) 15)]
    {:harja.ui.lomake/muokatut #{:kasittelyaika}
     :suorasanktio true
     :laji (oletus-uuden-sanktion-laji urakkatyyppi @valitun-urakan-sanktiolajit)
     :kasittelytapa (if (and (u-domain/mh-urakka? urakkatyyppi)
                          (>= (pvm/vuosi (:alkupvm @nav/valittu-urakka)) 2025))
                      :valikatselmus
                      nil)
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
                                             :alku alku
                                             :loppu loppu
                                             :vain-yllapitokohteettomat? vain-yllapitokohteettomat?
                                             :hae-sanktiot? hae-sanktiot?
                                             :hae-bonukset? hae-bonukset?}))

(def paivita-sanktiot-ja-bonukset-atom (atom false))
(defonce haetut-sanktiot-ja-bonukset
  (reaction<! [urakka (:id @nav/valittu-urakka)
               hoitokausi @urakka/valittu-hoitokausi
               _ @nakymassa?
               _ @paivita-sanktiot-ja-bonukset-atom]
    {:nil-kun-haku-kaynnissa? true}
    (when @nakymassa?
      (hae-urakan-sanktiot-ja-bonukset {:urakka-id urakka
                                        :alku (first hoitokausi)
                                        :loppu (second hoitokausi)}))))

(defn paivita-sanktiot-ja-bonukset!
  "Vaihtaa paivita-sanktiot-ja-bonukset atomin arvon, joka käynnistää sanktioiden ja bonusten haun."
  []
  (swap! paivita-sanktiot-ja-bonukset-atom not))

(defn hae-sanktion-liitteet!
  "Hakee sanktion liitteet urakan id:n ja sanktioon tietomallissa liittyvän laatupoikkeaman id:n
  perusteella."
  [urakka-id laatupoikkeama-id sanktio-atom]
  (log "hae-sanktion-liitteet!  " (pr-str urakka-id) " laatupoikkeama-id " (pr-str laatupoikkeama-id))
  (go (let [vastaus (<! (k/post! :hae-sanktion-liitteet {:urakka-id urakka-id
                                                         :laatupoikkeama-id laatupoikkeama-id}))]
        (if (k/virhe? vastaus)
          :virhe
          (swap! sanktio-atom assoc-in [:laatupoikkeama :liitteet] vastaus)))))

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
  (let [hoitokausi @urakka/valittu-hoitokausi]
    {:sanktio        (-> s
                       (dissoc :laatupoikkeama :yllapitokohde)
                       (assoc :paivamaara (first hoitokausi)
                         :soveltuvuuskonteksti :urakka))
     :laatupoikkeama (assoc (:laatupoikkeama s) :urakka urakka-id
                       :yllapitokohde (:id (:yllapitokohde s)))
     :hoitokausi     hoitokausi}))


(defn tallenna-sanktio
  [sanktio urakka-id onnistui-fn]
  (go
    (let [vastaus (<! (k/post! :tallenna-suorasanktio (kasaa-tallennuksen-parametrit sanktio urakka-id) nil true))]
      (if (k/virhe? vastaus)
        (viesti/nayta-toast!
          (or (k/virheviesti vastaus)
            "Sanktion tallennus epäonnistui!")
          :varoitus)
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

(defn suodata-sanktiot-ja-bonukset [sanktiot-ja-bonukset]
  (let [kaikki @urakan-lajisuodattimet
        valitut @sanktio-bonus-suodattimet]
    (if (= kaikki valitut)
      ;; Kaikki suodattimet valittu, ei suodateta mitään pois.
      sanktiot-ja-bonukset
      (filter
        #(valitut (domain-sanktio/rivin-tyyppi %))
        sanktiot-ja-bonukset))))

(defn valittavat-kulun-kohdistukset [toimenpideinstanssit sanktion-tyyppi]
  (case sanktion-tyyppi
    "Muut hoitourakan tehtäväkokonaisuudet" (remove
                                              #(str/includes? (str/lower-case (:tpi_nimi %)) "talvi")
                                              toimenpideinstanssit)
    "Talvihoito, päätiet" (filter
                            #(str/includes? (str/lower-case (:tpi_nimi %)) "talvi")
                            toimenpideinstanssit)
    "Talvihoito, muut tiet" (filter
                              #(str/includes? (str/lower-case (:tpi_nimi %)) "talvi")
                              toimenpideinstanssit)
    "Sorateiden hoito ja ylläpito" (filter
                                     #(or (str/includes? (str/lower-case (:tpi_nimi %)) "soratie")
                                        (str/includes? (str/lower-case (:tpi_nimi %)) "sorateiden"))
                                     toimenpideinstanssit)
    "Liikenneympäristön hoito" (filter
                                 #(str/includes? (str/lower-case (:tpi_nimi %)) "liikenne")
                                 toimenpideinstanssit)
    toimenpideinstanssit))

(defn mahdolliset-kulun-kohdistukset [suorasanktio? urakan-alkuvuosi sanktio-atom]
  (cond
    ;; MHU25+: näytetään vain Hoidonjohtopalkkio sekä suorasanktiolle että laatupoikkeaman sanktiolle
    (> urakan-alkuvuosi 2024)
    (filter #(= "23150" (:t2_koodi %)) @urakka/urakan-toimenpideinstanssit)

    ;; MHU24 tai vanhempi: suodatetaan tyypin mukaan
    :else
    (valittavat-kulun-kohdistukset @urakka/urakan-toimenpideinstanssit (get-in @sanktio-atom [:tyyppi :nimi]))))

(defn sanktion-tai-bonuksen-kuvaus [{:keys [suorasanktio laatupoikkeama] :as sanktio-tai-bonus}]
  ;; Bonuksilla ei tällä hetkellä ole kuvausta.
  ;; Näytetään sanktion kohde, mikäli kyseessä on suorasanktio, eli sanktio on tehty sanktiolomakkeella.
  ;; Jos kyse on laatupoikkeaman kautta tehdystä sanktiosta, näytetään kohteen kuvaus ja mahdollinen TR-osoite.
  (let [kohde (:kohde laatupoikkeama)]
    (if suorasanktio
      (or kohde "–")
      [:span
       (str "Laatupoikkeama: " kohde)
       [:br]
       (str (when (get-in laatupoikkeama [:tr :numero])
              (str " (" (tr/tierekisteriosoite-tekstina (:tr laatupoikkeama) {:teksti-tie? true}) ")")))])))
