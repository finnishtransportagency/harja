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
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.navigaatio :as nav])
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

;; Massat
(defrecord TallennaLomake [data])
(defrecord TallennaMassaOnnistui [vastaus])
(defrecord TallennaMassaEpaonnistui [vastaus])
(defrecord TyhjennaLomake [data])
(defrecord PaivitaMassaLomake [data])
(defrecord PaivitaAineenTieto [polku arvo])
(defrecord LisaaSideaine [sideaineen-kayttotapa])
(defrecord PoistaSideaine [sideaineen-kayttotapa])

;; Murskeet
(defrecord TallennaMurskeLomake [data])
(defrecord TallennaMurskeOnnistui [vastaus])
(defrecord TallennaMurskeEpaonnistui [vastaus])
(defrecord TyhjennaMurskeLomake [data])
(defrecord PaivitaMurskeLomake [data])


;; Haut
(defrecord HaePot2MassatJaMurskeet [])
(defrecord HaePot2MassatJaMurskeetOnnistui [vastaus])
(defrecord HaePot2MassatJaMurskeetEpaonnistui [vastaus])

(defrecord HaeKoodistot [])
(defrecord HaeKoodistotOnnistui [vastaus])
(defrecord HaeKoodistotEpaonnistui [vastaus])


(defn ainetyypin-koodi->nimi [ainetyypit koodi]
  (::pot2-domain/nimi (first
            (filter #(= (::pot2-domain/koodi %) koodi)
                    ainetyypit))))

(defn ainetyypin-koodi->lyhenne [ainetyypit koodi]
  (::pot2-domain/lyhenne (first
               (filter #(= (::pot2-domain/koodi %) koodi)
                       ainetyypit))))

(def asfalttirouheen-tyypin-id 2)

(defn massan-rc-pitoisuus
  "Palauttaa massan RC-pitoisuuden jos sellainen on (=asfalttirouheen massaprosentti)"
  [rivi]
  (when-let [runkoaineet (::pot2-domain/runkoaineet rivi)]
    (when-let [asfalttirouhe (first (filter #(= (:runkoaine/tyyppi %) asfalttirouheen-tyypin-id)
                                            runkoaineet))]
      (str "RC" (:runkoaine/massaprosentti asfalttirouhe)))))

(defn- rivin-avaimet->str
  ([rivi avaimet] (rivin-avaimet->str rivi avaimet " "))
  ([rivi avaimet separator]
   (str/join separator
             (remove nil? (mapv val (select-keys rivi avaimet))))))

(defn- massan-murskeen-nimen-komp [ydin tarkennukset fmt]
  (if (= :komponentti fmt)
   [:span
    [:span.bold ydin]
    [:span (when-not (empty? tarkennukset) (str " (" tarkennukset ")"))]]
   (str ydin (when-not (empty? tarkennukset) (str "(" tarkennukset ")")))))

(defn massan-rikastettu-nimi
  "Formatoi massan nimen. Jos haluat Reagent-komponentin, anna fmt = :komponentti, muuten anna :string"
  [massatyypit rivi fmt]
  ;; esim AB16 (AN15, RC40, 2020/09/1234) tyyppi (raekoko, nimen tarkenne, DoP, Kuulamyllyluokka, RC%)
  (let [rivi (assoc rivi ::pot2-domain/rc% (massan-rc-pitoisuus rivi))
        ydin (str (ainetyypin-koodi->lyhenne massatyypit (::pot2-domain/tyyppi rivi))
                  (rivin-avaimet->str rivi [::pot2-domain/max-raekoko
                                            ::pot2-domain/nimen-tarkenne
                                            ::pot2-domain/dop-nro]))

        tarkennukset (rivin-avaimet->str rivi [::pot2-domain/kuulamyllyluokka
                                               ::pot2-domain/rc%] ", ")]
    ;; vähän huonoksi ehkä meni tämän kanssa. Toinen funktiota kutsuva tarvitsee komponenttiwrapperin ja toinen ei
    ;; pitänee refaktoroida... fixme
    (if (= fmt :komponentti)
      [massan-murskeen-nimen-komp ydin tarkennukset fmt]
      (massan-murskeen-nimen-komp ydin tarkennukset fmt))))

(defn murskeen-rikastettu-nimi [mursketyypit rivi fmt]
  ;; esim KaM LJYR 2020/09/3232 (0/40, LA30)
  ;; tyyppi Kalliomurske, tarkenne LJYR, rakeisuus 0/40, iskunkestävyys (esim LA30)
  (let [ydin (str (ainetyypin-koodi->lyhenne mursketyypit (::pot2-domain/tyyppi rivi)) " "
                  (rivin-avaimet->str rivi #{::pot2-domain/tyyppi ::pot2-domain/nimen-tarkenne ::pot2-domain/dop-nro}))
        tarkennukset (rivin-avaimet->str rivi #{::pot2-domain/rakeisuus ::pot2-domain/iskunkestavyys} ", ")]
    (if (= fmt :komponentti)
      [massan-murskeen-nimen-komp ydin tarkennukset fmt]
      (massan-murskeen-nimen-komp ydin tarkennukset fmt))))

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
    (js/console.log "Uusi massa lomake avataan")
    (assoc app :pot2-massa-lomake {}))

  MuokkaaMassaa
  (process-event [{rivi :rivi klooni? :klooni?} app]
    ;; jos käyttäjä luo uuden massan kloonaamalla vanhan, nollataan id:t
    (let [massan-id (if klooni?
                      nil
                      (::pot2-domain/massa-id rivi))
          runkoaineet (aine-kayttoliittyman-muotoon (map
                                                      ;; Koska luodaan uusi massa olemassaolevan tietojen pohjalta, täytyy vanhan massan viittaukset poistaa
                                                      #(if klooni?
                                                         (dissoc % :runkoaine/id ::pot2-domain/massa-id)
                                                         (identity %))
                                                      (:harja.domain.pot2/runkoaineet rivi)) :runkoaine/tyyppi)
          sideaineet (sideaine-kayttoliittyman-muotoon (map
                                                         #(if klooni?
                                                            (dissoc % :sideaine/id ::pot2-domain/massa-id)
                                                            (identity %))
                                                         (:harja.domain.pot2/sideaineet rivi)))
          lisaaineet (aine-kayttoliittyman-muotoon (map
                                                     #(if klooni?
                                                        (dissoc % :lisaaine/id ::pot2-domain/massa-id)
                                                        (identity %))
                                                     (:harja.domain.pot2/lisaaineet rivi)) :lisaaine/tyyppi)]
      (-> app
          (assoc :pot2-massa-lomake rivi)
          (assoc-in [:pot2-massa-lomake ::pot2-domain/massa-id] massan-id)
          (assoc-in [:pot2-massa-lomake :harja.domain.pot2/runkoaineet] runkoaineet)
          (assoc-in [:pot2-massa-lomake :harja.domain.pot2/sideaineet] sideaineet)
          (assoc-in [:pot2-massa-lomake :harja.domain.pot2/lisaaineet] lisaaineet))))

  UusiMurske
  (process-event [_ app]
    (js/console.log "Uusi murskelomake avataan")
    (assoc app :pot2-murske-lomake {}))

  MuokkaaMursketta
  (process-event [{rivi :rivi klooni? :klooni?} app]
    ;; jos käyttäjä luo uuden massan kloonaamalla vanhan, nollataan id:t
    (let [murske-id (if klooni? nil (::pot2-domain/murske-id rivi))]
      (-> app
          (assoc :pot2-murske-lomake rivi)
          (assoc-in [:pot2-murske-lomake ::pot2-domain/murske-id] murske-id))))

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
                                                      (:harja.domain.pot2/poistettu? data))}
          _ (js/console.log "TallennaLomake data" (pr-str data))
          _ (js/console.log "TallennaLomake massa" (pr-str massa))]
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
      (viesti/nayta! "Massa poistettu!")
      (viesti/nayta! "Massa tallennettu!"))
    (hae-massat-ja-murskeet app)
    (assoc app :pot2-massa-lomake nil))

  TallennaMassaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Massan tallennus epäonnistui!" :danger)
    app)

  TyhjennaLomake
  (process-event [{data :data} app]
    (js/console.log "TyhjennaLomake" (pr-str data))
    (-> app
        (assoc :pot2-massa-lomake nil)))

  PaivitaMurskeLomake
  (process-event [{data :data} app]
    (update app :pot2-murske-lomake merge data))

  TallennaMurskeLomake
  (process-event [{data :data} app]
    (let [murske (:pot2-murske-lomake app)
          poistettu? {::harja.domain.pot2/poistettu? (boolean
                                                       (:harja.domain.pot2/poistettu? data))}
          _ (js/console.log "TallennaMurskeLomake data" (pr-str data))
          _ (js/console.log "TallennaMurskeLomake murske" (pr-str murske))]
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
      (viesti/nayta! "Massa poistettu!")
      (viesti/nayta! "Massa tallennettu!"))
    (hae-massat-ja-murskeet app)
    (assoc app :pot2-murske-lomake nil))

  TallennaMurskeEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Murskeen tallennus epäonnistui!" :danger)
    app)

  TyhjennaMurskeLomake
  (process-event [{data :data} app]
    (js/console.log "TyhjennaLomake" (pr-str data))
    (-> app
        (assoc :pot2-murske-lomake nil)))
  )