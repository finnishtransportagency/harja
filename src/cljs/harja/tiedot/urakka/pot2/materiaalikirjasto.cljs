(ns harja.tiedot.urakka.pot2.materiaalikirjasto
  "UI controlleri pot2 materiaalikirjastolle"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [clojure.string :as str]
            [harja.loki :refer [log tarkkaile!]]
            [tuck.core :refer [process-event] :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.pot2 :as pot2-domain]
            [harja.pvm :as pvm]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as tila])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def materiaalikirjastossa? (atom false))
(def nayta-materiaalikirjasto? (atom false))

(defrecord AlustaTila [])
(defrecord UusiMassa [])
(defrecord MuokkaaMassaa [rivi klooni?])
(defrecord UusiMurske [])
(defrecord MuokkaaMursketta [rivi klooni?])
(defrecord NaytaModal [avataanko?])
(defrecord AloitaMuokkaus [lomakepolku])

;; Massat
(defrecord TallennaLomake [data])
(defrecord TallennaMassaOnnistui [vastaus])
(defrecord TallennaMassaEpaonnistui [vastaus])
(defrecord TyhjennaLomake [])
(defrecord PaivitaMassaLomake [data])
(defrecord PaivitaAineenTieto [polku arvo])
(defrecord LisaaSideaine [sideaineen-kayttotapa])
(defrecord PoistaSideaine [sideaineen-kayttotapa])

;; Murskeet
(defrecord TallennaMurskeLomake [data])
(defrecord TallennaMurskeOnnistui [vastaus])
(defrecord TallennaMurskeEpaonnistui [vastaus])
(defrecord SuljeMurskeLomake [])
(defrecord PaivitaMurskeLomake [data])


;; Haut
(defrecord HaePot2MassatJaMurskeet [])
(defrecord HaePot2MassatJaMurskeetOnnistui [vastaus])
(defrecord HaePot2MassatJaMurskeetEpaonnistui [vastaus])

(defrecord HaeKoodistot [])
(defrecord HaeKoodistotOnnistui [vastaus])
(defrecord HaeKoodistotEpaonnistui [vastaus])

(defn materiaalikirjasto-tyhja?
  [massat murskeet]
  (and (empty? massat)
       (empty? murskeet)))

(defn massatyypit-vai-mursketyypit? [tyypit]
  (if (some (fn [lyhenne] (= lyhenne "AB"))
            (map ::pot2-domain/lyhenne tyypit))
    :massa
    :murske))

(def sideaineen-kayttotavat
  [{::pot2-domain/nimi "Lopputuotteen sideaine"
    ::pot2-domain/koodi :lopputuote}
   {::pot2-domain/nimi "Lisätty sideaine"
    ::pot2-domain/koodi :lisatty}])

(defn lisatty-sideaine-mahdollinen?
  [rivi]
  (let [asfalttirouhetta? ((set (keys (::pot2-domain/runkoaineet rivi))) 2)
        bitumikaterouhetta? ((set (keys (::pot2-domain/lisaaineet rivi))) 4)]
    (or asfalttirouhetta? bitumikaterouhetta?)))

(defn- sideaine-kayttoliittyman-muotoon
  "UI kilkkeet tarvitsevat runko-, side- ja lisäaineet muodossa {tyyppi {tiedot}}"
  [aineet]
  (let [lopputuotteen (remove #(false? (:sideaine/lopputuote? %)) aineet)
        lisatyt (remove #(true? (:sideaine/lopputuote? %)) aineet)
        aineet-map (fn [aineet]
                     (if (empty? aineet)
                       {}
                       {:valittu? true
                        :aineet (into {}
                                      (map-indexed
                                        (fn [idx aine]
                                          {idx aine})
                                        (vec aineet)))}))]
    {:lopputuote (aineet-map lopputuotteen)
     :lisatty (aineet-map lisatyt)}))

(defn- aine-kayttoliittyman-muotoon
  "UI kilkkeet tarvitsevat runko- ja lisäaineet muodossa {tyyppi {tiedot}}"
  [aineet avain]
  (into {}
        (map (fn [aine]
               {(avain aine) (assoc aine :valittu? true)})
             (vec aineet))))

(defn- hae-massat-ja-murskeet [app]
  (tuck-apurit/post! app
                     :hae-urakan-massat-ja-murskeet
                     {:urakka-id (-> @tila/tila :yleiset :urakka :id)}
                     {:onnistui ->HaePot2MassatJaMurskeetOnnistui
                      :epaonnistui ->HaePot2MassatJaMurskeetEpaonnistui}))
(def tyhja-sideaine
  {:sideaine/tyyppi nil :sideaine/pitoisuus nil})

(defn mursketyyppia? [mursketyypit nimi lomake]
  (= (pot2-domain/ainetyypin-nimi->koodi mursketyypit nimi)
     (::pot2-domain/tyyppi lomake)))

(defn mursketyyppia-bem-tai-muu? [mursketyypit lomake]
  (or (mursketyyppia? mursketyypit "(UUSIO) Betonimurske I" lomake)
      (mursketyyppia? mursketyypit "(UUSIO) Betonimurske II" lomake)
      (mursketyyppia? mursketyypit "Muu" lomake)))

(def nayta-lahde mursketyyppia-bem-tai-muu?)

(defn massa-kayttoliittyman-muotoon
  [massa id klooni?]
  (let [runkoaineet (aine-kayttoliittyman-muotoon (map
                                                    ;; Koska luodaan uusi massa olemassaolevan tietojen pohjalta, täytyy vanhan massan viittaukset poistaa
                                                    #(if klooni?
                                                       (dissoc % :runkoaine/id ::pot2-domain/massa-id)
                                                       (identity %))
                                                    (:harja.domain.pot2/runkoaineet massa)) :runkoaine/tyyppi)
        sideaineet (sideaine-kayttoliittyman-muotoon (map
                                                       #(if klooni?
                                                          (dissoc % :sideaine/id ::pot2-domain/massa-id)
                                                          (identity %))
                                                       (:harja.domain.pot2/sideaineet massa)))
        lisaaineet (aine-kayttoliittyman-muotoon (map
                                                   #(if klooni?
                                                      (dissoc % :lisaaine/id ::pot2-domain/massa-id)
                                                      (identity %))
                                                   (:harja.domain.pot2/lisaaineet massa)) :lisaaine/tyyppi)]
    (-> massa
        (assoc ::pot2-domain/massa-id id
               :harja.domain.pot2/runkoaineet runkoaineet
               :harja.domain.pot2/sideaineet sideaineet
               :harja.domain.pot2/lisaaineet lisaaineet))))

(defn- lisa-aineisiin-pitoisuus [rivi aineet]
  (mapv (fn [aine]
          (let [pitoisuus-rivissa (:lisaaine/pitoisuus
                                    (first (filter (fn [la]
                                                     (= (::pot2-domain/koodi aine)
                                                        (:lisaaine/tyyppi la)))
                                                   (vals (get-in rivi [:data ::pot2-domain/lisaaineet])))))]
            (assoc aine ::pot2-domain/pitoisuus pitoisuus-rivissa)))
        aineet))

(defn jarjesta-aineet-tarvittaessa
  "Järjestää tarvittaessa esim massan lisäaineet pitoisuuden mukaisesti suurimmasta pienimpään"
  [{:keys [rivi tyyppi aineet voi-muokata?] :as opts}]
  (if (and (= tyyppi :lisaaineet)
           (not voi-muokata?))
    (into []
          (reverse
            (sort-by ::pot2-domain/pitoisuus
                     (lisa-aineisiin-pitoisuus rivi aineet))))
    aineet))

(def uusi-massa-map
  {:harja.domain.pot2/sideaineet {:lopputuote {:valittu? true}}})

(extend-protocol tuck/Event

  AlustaTila
  (process-event [_ {:as app}]
    (assoc app :pot2-massa-lomake nil))

  NaytaModal
  (process-event [{avataanko? :avataanko?} app]
    (do
      (reset! nayta-materiaalikirjasto? avataanko?)
      app))

  HaePot2MassatJaMurskeet
  (process-event [_ app]
    (-> app
        (hae-massat-ja-murskeet)))

  HaePot2MassatJaMurskeetOnnistui
  (process-event [{{massat :massat
                    murskeet :murskeet} :vastaus} {:as app}]
    (assoc app :massat massat
               :murskeet murskeet))

  HaePot2MassatJaMurskeetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Massojen haku epäonnistui!" :danger)
    app)

  HaeKoodistot
  (process-event [_ app]
    (if-not (:materiaalikoodistot app)
      (-> app
         (tuck-apurit/post! :hae-pot2-koodistot
                            {:urakka-id (-> @tila/tila :yleiset :urakka :id)}
                            {:onnistui ->HaeKoodistotOnnistui
                             :epaonnistui ->HaeKoodistotEpaonnistui}))
      app))

  HaeKoodistotOnnistui
  (process-event [{vastaus :vastaus} {:as app}]
    (assoc app :materiaalikoodistot vastaus))

  HaeKoodistotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Materiaalikoodistojen haku epäonnistui!" :danger)
    app)

  UusiMassa
  (process-event [_ app]
    (assoc app :pot2-massa-lomake uusi-massa-map))

  MuokkaaMassaa
  (process-event [{rivi :rivi klooni? :klooni?} app]
    ;; jos käyttäjä luo uuden massan kloonaamalla vanhan, nollataan id:t
    (let [massan-id (if klooni?
                      nil
                      (::pot2-domain/massa-id rivi))
          massa (massa-kayttoliittyman-muotoon rivi massan-id klooni?)]
      (-> app
          (assoc :pot2-massa-lomake massa))))

  UusiMurske
  (process-event [_ app]
    (assoc app :pot2-murske-lomake {}))

  MuokkaaMursketta
  (process-event [{rivi :rivi klooni? :klooni?} app]
    ;; jos käyttäjä luo uuden massan kloonaamalla vanhan, nollataan id:t ja käytössäoleminen
    (let [murske-id (if klooni? nil (::pot2-domain/murske-id rivi))
          kaytossa (if klooni? nil (::pot2-domain/kaytossa rivi))]
      (-> app
          (assoc :pot2-murske-lomake rivi)
          (assoc-in [:pot2-murske-lomake ::pot2-domain/murske-id] murske-id)
          (assoc-in [:pot2-murske-lomake ::pot2-domain/kaytossa] kaytossa))))

  PaivitaMassaLomake
  (process-event [{data :data} app]
    (update app :pot2-massa-lomake merge data))

  PaivitaAineenTieto
  (process-event [{polku :polku arvo :arvo} app]
    (assoc-in app
              (vec (cons :pot2-massa-lomake polku)) arvo))

  LisaaSideaine
  (process-event [{sideaineen-kayttotapa :sideaineen-kayttotapa} app]
    (let [aineiden-lkm
          (count (get-in app
                         [:pot2-massa-lomake ::pot2-domain/sideaineet sideaineen-kayttotapa :aineet]))]
      (assoc-in app
                [:pot2-massa-lomake ::pot2-domain/sideaineet sideaineen-kayttotapa :aineet aineiden-lkm]
                tyhja-sideaine)))

  PoistaSideaine
  (process-event [{sideaineen-kayttotapa :sideaineen-kayttotapa} app]
    (let [aineiden-lkm
          (count (get-in app
                         [:pot2-massa-lomake ::pot2-domain/sideaineet sideaineen-kayttotapa :aineet]))]
      (update-in app [:pot2-massa-lomake ::pot2-domain/sideaineet sideaineen-kayttotapa :aineet]
                 dissoc (dec aineiden-lkm))))

  TallennaLomake
  (process-event [{data :data} app]
    (let [massa (:pot2-massa-lomake app)
          poistettu? {::harja.domain.pot2/poistettu? (boolean
                                                      (:harja.domain.pot2/poistettu? data))}]
      (tuck-apurit/post! :tallenna-urakan-massa
                         (-> (merge massa
                                    poistettu?)
                             (assoc ::pot2-domain/urakka-id (-> @tila/tila :yleiset :urakka :id)))
                         {:onnistui ->TallennaMassaOnnistui
                          :epaonnistui ->TallennaMassaEpaonnistui}))
    app)

  TallennaMassaOnnistui
  (process-event [{vastaus :vastaus} app]
    (if (::pot2-domain/poistettu? vastaus)
      (viesti/nayta-toast! "Massa poistettu!")
      (viesti/nayta-toast! "Massa tallennettu!"))
    (hae-massat-ja-murskeet app)
    (assoc app :pot2-massa-lomake nil))

  TallennaMassaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! "Massan tallennus epäonnistui!" :varoitus)
    app)

  TyhjennaLomake
  (process-event [_ app]
    (-> app
        (assoc :pot2-massa-lomake nil)))

  PaivitaMurskeLomake
  (process-event [{data :data} app]
    (update app :pot2-murske-lomake merge data))

  TallennaMurskeLomake
  (process-event [{data :data} app]
    (let [murske (:pot2-murske-lomake app)
          poistettu? {::harja.domain.pot2/poistettu? (boolean
                                                       (:harja.domain.pot2/poistettu? data))}]
      (tuck-apurit/post! :tallenna-urakan-murske
                         (-> (merge murske
                                    poistettu?)
                             (assoc ::pot2-domain/urakka-id (-> @tila/tila :yleiset :urakka :id)))
                         {:onnistui ->TallennaMurskeOnnistui
                          :epaonnistui ->TallennaMurskeEpaonnistui}))
    app)

  TallennaMurskeOnnistui
  (process-event [{vastaus :vastaus} app]
    (if (::pot2-domain/poistettu? vastaus)
      (viesti/nayta-toast! "Murske poistettu!")
      (viesti/nayta-toast! "Murske tallennettu!"))
    (hae-massat-ja-murskeet app)
    (assoc app :pot2-murske-lomake nil))

  TallennaMurskeEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Murskeen tallennus epäonnistui!" :danger)
    app)

  SuljeMurskeLomake
  (process-event [_ app]
    (-> app
        (assoc :pot2-murske-lomake nil)))

  AloitaMuokkaus
  (process-event [{lomakepolku :lomakepolku} app]
    (js/console.log "AloitaMuokkaus" (pr-str lomakepolku))
    (-> app
        (assoc-in [lomakepolku :voi-muokata?] true))))