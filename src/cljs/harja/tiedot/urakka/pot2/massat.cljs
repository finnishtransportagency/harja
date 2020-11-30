(ns harja.tiedot.urakka.pot2.massat
  "UI controlleri pot2 massoille"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log tarkkaile!]]
            [tuck.core :refer [process-event] :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.pot2 :as pot2-domain]
            [harja.pvm :as pvm]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def materiaalikirjastossa? (atom false))
(def nayta-materiaalikirjasto? (atom false))

(defrecord AlustaTila [])
(defrecord UusiMassa [avaa-massa-lomake?])
(defrecord MuokkaaMassaa [rivi klooni?])
(defrecord NaytaModal [avataanko?])

(defrecord TallennaLomake [data])
(defrecord TallennaMassaOnnistui [vastaus])
(defrecord TallennaMassaEpaonnistui [vastaus])
(defrecord TyhjennaLomake [data])
(defrecord PaivitaLomake [data])
(defrecord PaivitaAineenTieto [polku arvo])
(defrecord LisaaSideaine [sideaineen-kayttotapa])
(defrecord PoistaSideaine [sideaineen-kayttotapa])

;; Haut
(defrecord HaePot2Massat [])
(defrecord HaePot2MassatOnnistui [vastaus])
(defrecord HaePot2MassatEpaonnistui [vastaus])

(defrecord HaeKoodistot [])
(defrecord HaeKoodistotOnnistui [vastaus])
(defrecord HaeKoodistotEpaonnistui [vastaus])

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

(defn- hae-massat [app]
  (tuck-apurit/post! app
                     :hae-urakan-pot2-massat
                     {:urakka-id (-> @tila/tila :yleiset :urakka :id)}
                     {:onnistui ->HaePot2MassatOnnistui
                      :epaonnistui ->HaePot2MassatEpaonnistui}))
(def tyhja-sideaine
  {:sideaine/tyyppi nil :sideaine/pitoisuus nil})

(extend-protocol tuck/Event

  AlustaTila
  (process-event [_ {:as app}]
    (assoc app :pot2-massa-lomake nil))

  NaytaModal
  (process-event [{avataanko? :avataanko?} app]
    (do
      (reset! nayta-materiaalikirjasto? avataanko?)
      app))

  HaePot2Massat
  (process-event [_ app]
    (-> app
        (hae-massat)))

  HaePot2MassatOnnistui
  (process-event [{vastaus :vastaus} {:as app}]
    (assoc app :massat vastaus))

  HaePot2MassatEpaonnistui
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
  (process-event [{avaa-massa-lomake? :avaa-massa-lomake?} app]
    (js/console.log "Uusi massa lomake avataan" avaa-massa-lomake?)
    (assoc app :avaa-massa-lomake? avaa-massa-lomake?
               :pot2-massa-lomake nil))

  MuokkaaMassaa
  (process-event [{rivi :rivi klooni? :klooni?} app]
    ;; jos käyttäjä luo uuden massan kloonaamalla vanhan, nollataan id:t
    (let [massan-id (if klooni?
                      nil
                      (:pot2-massa/id rivi))
          runkoaineet (aine-kayttoliittyman-muotoon (map
                                                      ;; Koska luodaan uusi massa olemassaolevan tietojen pohjalta, täytyy vanhan massan viittaukset poistaa
                                                      #(if klooni?
                                                         (dissoc % :runkoaine/id :pot2-massa/id)
                                                         (identity %))
                                                      (:harja.domain.pot2/runkoaineet rivi)) :runkoaine/tyyppi)
          sideaineet (sideaine-kayttoliittyman-muotoon (map
                                                         #(if klooni?
                                                            (dissoc % :sideaine/id :pot2-massa/id)
                                                            (identity %))
                                                         (:harja.domain.pot2/sideaineet rivi)))
          lisaaineet (aine-kayttoliittyman-muotoon (map
                                                     #(if klooni?
                                                        (dissoc % :lisaaine/id :pot2-massa/id)
                                                        (identity %))
                                                     (:harja.domain.pot2/lisaaineet rivi)) :lisaaine/tyyppi)]
      (-> app
          (assoc :avaa-massa-lomake? true
                 :pot2-massa-lomake rivi)
          (assoc-in [:pot2-massa-lomake :pot2-massa/id] massan-id)
          (assoc-in [:pot2-massa-lomake :harja.domain.pot2/runkoaineet] runkoaineet)
          (assoc-in [:pot2-massa-lomake :harja.domain.pot2/sideaineet] sideaineet)
          (assoc-in [:pot2-massa-lomake :harja.domain.pot2/lisaaineet] lisaaineet))))

  PaivitaLomake
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
                                                      (:harja.domain.pot2/poistettu? data))}
          _ (js/console.log "TallennaLomake data" (pr-str data))
          _ (js/console.log "TallennaLomake massa" (pr-str massa))]
      (tuck-apurit/post! :tallenna-urakan-pot2-massa
                         (-> (merge massa
                                    poistettu?)
                             (assoc ::pot2-domain/urakka-id (-> @tila/tila :yleiset :urakka :id)))
                         {:onnistui ->TallennaMassaOnnistui
                          :epaonnistui ->TallennaMassaEpaonnistui}))
    app)

  TallennaMassaOnnistui
  (process-event [{vastaus :vastaus} app]
    (if (::pot2-domain/poistettu? vastaus)
      (viesti/nayta! "Massa poistettu!")
      (viesti/nayta! "Massa tallennettu!"))
    (hae-massat nil)
    (assoc app :avaa-massa-lomake? false))

  TallennaMassaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Massan tallennus epäonnistui!" :danger)
    app)

  TyhjennaLomake
  (process-event [{data :data} app]
    (js/console.log "TyhjennaLomake" (pr-str data))
    (-> app
        (assoc :pot2-massa-lomake nil
               :avaa-massa-lomake? false))))