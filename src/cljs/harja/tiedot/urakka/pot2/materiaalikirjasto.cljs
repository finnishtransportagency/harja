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
(defrecord PoistaMassa [id])
(defrecord PoistaMassaOnnistui [vastaus])
(defrecord PoistaMassaEpaonnistui [vastaus])
(defrecord TyhjennaLomake [])
(defrecord PaivitaMassaLomake [data])
(defrecord PaivitaAineenTieto [polku arvo])
(defrecord LisaaSideaine [sideaineen-kayttotapa])
(defrecord PoistaSideaine [sideaineen-kayttotapa])
(defrecord ValitseMassaTaiMurske [id tyyppi])
(defrecord ValitseKaikkiMassatTaiMurskeet [tyyppi])

;; Murskeet
(defrecord TallennaMurskeLomake [data])
(defrecord TallennaMurskeOnnistui [vastaus])
(defrecord TallennaMurskeEpaonnistui [vastaus])
(defrecord PoistaMurske [id])
(defrecord PoistaMurskeOnnistui [vastaus])
(defrecord PoistaMurskeEpaonnistui [vastaus])
(defrecord SuljeMurskeLomake [])
(defrecord SuljeMateriaaliModal [])
(defrecord PaivitaMurskeLomake [data])


;; Haut
(defrecord HaePot2MassatJaMurskeet [])
(defrecord HaePot2MassatJaMurskeetOnnistui [vastaus])
(defrecord HaePot2MassatJaMurskeetEpaonnistui [vastaus])
(defrecord HaeMuutUrakatJoissaMateriaaleja [])
(defrecord HaeMuutUrakatJoissaMateriaalejaOnnistui [vastaus])
(defrecord HaeMuutUrakatJoissaMateriaalejaEpaonnistui [vastaus])
(defrecord SuljeMuistaUrakoistaTuonti [])
(defrecord HaeMateriaalitToisestaUrakasta [urakka-id])
(defrecord HaeMateriaalitToisestaUrakastaOnnistui [vastaus])
(defrecord HaeMateriaalitToisestaUrakastaEpaonnistui [vastaus])
(defrecord ValitseTuontiUrakka [urakka-id])
(defrecord TuoMateriaalitToisestaUrakasta [])
(defrecord TuoMateriaalitToisestaUrakastaOnnistui [vastaus])
(defrecord TuoMateriaalitToisestaUrakastaEpaonnistui [vastaus])

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

(def uuden-materiaalin-nimen-vihje "Nimi muodostetaan automaattisesti lomakkeeseen täytettyjen tietojen perusteella")

(def uusi-massa-map
  {::pot2-domain/massan-nimi uuden-materiaalin-nimen-vihje
   :harja.domain.pot2/sideaineet {:lopputuote {:valittu? true}}})

(def uusi-murske-map
  {::pot2-domain/murskeen-nimi uuden-materiaalin-nimen-vihje})

(defn- materiaalin-nimen-komp
  "Materiaalin nimi voidaan esittää joko Reagent-komponenttina tai stringinä (default), käyttötapauksesta riippuen."
  [{:keys [ydin tarkennukset fmt toiminto-fn]}]
  (if (= :komponentti fmt)
    [(if toiminto-fn :div :span) ;; Jos toiminto-fn annetaan, kääritään komponentti diviin, muuten spaniin.
     {:on-click #(when toiminto-fn
                   (do
                     (.stopPropagation %)
                     (toiminto-fn)))
      :style {:cursor "pointer"}}
     [:span.bold ydin]
     ;; Toistaiseksi Tean kanssa sovittu 23.2.2021 ettei näytetä tarkennuksia suluissa
     [:span tarkennukset]]
    (str ydin tarkennukset)))

(defn materiaalin-rikastettu-nimi
  "Formatoi massan tai murskeen nimen. Jos haluat Reagent-komponentin, anna fmt = :komponentti, muuten anna :string"
  [{:keys [tyypit materiaali fmt toiminto-fn]}]
  ;; esim AB16 (AN15, RC40, 2020/09/1234) tyyppi (raekoko, nimen tarkenne, DoP, Kuulamyllyluokka, RC%)
  (let [tyyppi (massatyypit-vai-mursketyypit? tyypit)
        [ydin tarkennukset] ((if (= :massa tyyppi)
                               pot2-domain/massan-rikastettu-nimi
                               pot2-domain/murskeen-rikastettu-nimi)
                             tyypit materiaali)
        params {:ydin ydin
                :tarkennukset tarkennukset
                :fmt fmt :toiminto-fn toiminto-fn}]
    (if (= fmt :komponentti)
      [materiaalin-nimen-komp params]
      (materiaalin-nimen-komp params))))

(defn- hae-muut-urakat-joissa-materiaaleja [app]
  (tuck-apurit/post! app
                     :hae-muut-urakat-joissa-materiaaleja
                     {:urakka-id (-> @tila/tila :yleiset :urakka :id)}
                     {:onnistui ->HaeMuutUrakatJoissaMateriaalejaOnnistui
                      :epaonnistui ->HaeMuutUrakatJoissaMateriaalejaEpaonnistui}))

(defn- rikasta-materiaalien-nimi
  "Rikastaa tuotujen materiaalien nimen ja asettaa oletuksen valittu? false"
  [app massat murskeet]
  (let [massat (map #(assoc % ::pot2-domain/massan-nimi
                              (materiaalin-rikastettu-nimi {:tyypit (get-in app [:materiaalikoodistot :massatyypit])
                                                            :materiaali %})
                              :valittu? false)
                    massat)
        murskeet  (map #(assoc % ::pot2-domain/murskeen-nimi
                                 (materiaalin-rikastettu-nimi {:tyypit (get-in app [:materiaalikoodistot :mursketyypit])
                                                               :materiaali %})
                                 :valittu? false)
                       murskeet)]
    {:massat massat
     :murskeet murskeet}))

(defn materiaalien-ruksin-tila [rivit]
  (cond
    (every? :valittu? rivit) true

    (not-any? :valittu? rivit) false

    :else
    :harja.ui.kentat/indeterminate))

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
                    murskeet :murskeet} :vastaus} app]
    (let [{massat :massat
           murskeet :murskeet} (rikasta-materiaalien-nimi app massat murskeet)]
      (assoc app :massat massat
                 :murskeet murskeet)))

  HaePot2MassatJaMurskeetEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Massojen haku epäonnistui!" :danger)
    app)

  HaeMuutUrakatJoissaMateriaaleja
  (process-event [_ app]
    (-> app
        (hae-muut-urakat-joissa-materiaaleja)))

  HaeMuutUrakatJoissaMateriaalejaOnnistui
  (process-event [{vastaus :vastaus} app]
    (assoc app :muut-urakat-joissa-materiaaleja vastaus
               :nayta-muista-urakoista-tuonti? true))

  HaeMuutUrakatJoissaMateriaalejaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! (str "Muiden urakoiden haku materiaalien tuontia varten epäonnistui!"
                        vastaus) :danger)
    app)

  SuljeMuistaUrakoistaTuonti
  (process-event [_ app]
    (assoc app :tuonti-urakka nil
               :nayta-muista-urakoista-tuonti? false
               :muut-urakat-joissa-materiaaleja nil
               :materiaalit-toisesta-urakasta nil))

  HaeMateriaalitToisestaUrakasta
  (process-event [{urakka-id :urakka-id} app]
    (-> app
        (tuck-apurit/post! :hae-urakan-massat-ja-murskeet
                           {:urakka-id urakka-id}
                           {:onnistui ->HaeMateriaalitToisestaUrakastaOnnistui
                            :epaonnistui ->HaeMateriaalitToisestaUrakastaEpaonnistui})))


  HaeMateriaalitToisestaUrakastaOnnistui
  (process-event [{vastaus :vastaus} app]
    (let [{massat :massat
           murskeet :murskeet} vastaus]
      (assoc app :materiaalit-toisesta-urakasta (rikasta-materiaalien-nimi app massat murskeet))))

  HaeMateriaalitToisestaUrakastaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Materiaalien haku toisesta urakasta epäonnistui!" :danger)
    app)

  ValitseTuontiUrakka
  (process-event [{urakka-id :urakka-id} app]
    (-> app
        (assoc :tuonti-urakka urakka-id)
        (tuck-apurit/post! :hae-urakan-massat-ja-murskeet
                           {:urakka-id urakka-id}
                           {:onnistui ->HaeMateriaalitToisestaUrakastaOnnistui
                            :epaonnistui ->HaeMateriaalitToisestaUrakastaEpaonnistui})))

  TuoMateriaalitToisestaUrakasta
  (process-event [_ app]
    (let [massa-idt (keep #(when (true? (:valittu? %))
                             (::pot2-domain/massa-id %))
                          (get-in app [:materiaalit-toisesta-urakasta :massat]))
          murske-idt (keep #(when (true? (:valittu? %))
                                (::pot2-domain/murske-id %))
                            (get-in app [:materiaalit-toisesta-urakasta :murskeet]))]
      (-> app
          (tuck-apurit/post! :tuo-materiaalit-toisesta-urakasta
                             {:urakka-id (-> @tila/tila :yleiset :urakka :id)
                              :massa-idt massa-idt
                              :murske-idt murske-idt}
                             {:onnistui ->TuoMateriaalitToisestaUrakastaOnnistui ;; sama paluuarvo eri kutsussa, joten sama käsittelijä
                              :epaonnistui ->TuoMateriaalitToisestaUrakastaEpaonnistui}))))


  TuoMateriaalitToisestaUrakastaOnnistui
  (process-event [{{massat :massat
                    murskeet :murskeet} :vastaus} app]
    (let [{massat :massat
           murskeet :murskeet} (rikasta-materiaalien-nimi app massat murskeet)]
      (viesti/nayta-toast! "Materiaalien tuonti toisesta urakasta onnistui")
      (assoc app :massat massat
                 :murskeet murskeet
                 :tuonti-urakka nil
                 :materiaalit-toisesta-urakasta nil
                 :muut-urakat-joissa-materiaaleja nil
                 :nayta-muista-urakoista-tuonti? false)))

  TuoMateriaalitToisestaUrakastaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Materiaalien tuonti toisesta urakasta epäonnistui!" :danger)
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
  (process-event [{vastaus :vastaus} app]
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
          massa (massa-kayttoliittyman-muotoon rivi massan-id klooni?)
          kaytossa (if klooni? nil (::pot2-domain/kaytossa rivi))]
      (-> app
          (assoc :pot2-massa-lomake massa)
          (assoc-in [:pot2-massa-lomake ::pot2-domain/kaytossa] kaytossa))))

  UusiMurske
  (process-event [_ app]
    (assoc app :pot2-murske-lomake uusi-murske-map))

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
    (let [uudet-tiedot (assoc data ::pot2-domain/massan-nimi
                                   (materiaalin-rikastettu-nimi {:tyypit (get-in app [:materiaalikoodistot :massatyypit])
                                                                 :materiaali data}))]
      (update app :pot2-massa-lomake merge uudet-tiedot)))

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

  ValitseMassaTaiMurske
  (process-event [{id :id tyyppi :tyyppi} app]
    (let [tunniste (if (= tyyppi :massat)
                     ::pot2-domain/massa-id
                     ::pot2-domain/murske-id)
          rivit (mapv #(if (= (tunniste %) id)
                          (assoc % :valittu? (not (:valittu? %)))
                          %)
                       (get-in app [:materiaalit-toisesta-urakasta tyyppi]))]
      (assoc-in app [:materiaalit-toisesta-urakasta tyyppi] rivit)))

  ValitseKaikkiMassatTaiMurskeet
  (process-event [{tyyppi :tyyppi} app]
    (let [rivit (get-in app [:materiaalit-toisesta-urakasta tyyppi])
          tila-ennen (materiaalien-ruksin-tila rivit)
          rivit (mapv #(assoc % :valittu? (if (true? tila-ennen)
                                            false
                                            true))
                      rivit)]
      (assoc-in app [:materiaalit-toisesta-urakasta tyyppi] rivit)))

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

  PoistaMassa
  (process-event [{id :id} app]
    (tuck-apurit/post! :poista-urakan-massa
                       {:id id}
                       {:onnistui ->PoistaMassaOnnistui
                        :epaonnistui ->PoistaMassaEpaonnistui
                        :paasta-virhe-lapi? true})
    app)

  PoistaMassaOnnistui
  (process-event [{massat :massat
                   murskeet :murskeet} app]
    (viesti/nayta-toast! "Massa poistettu onnistuneesti")
    (hae-massat-ja-murskeet app)
    app)

  PoistaMassaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Massan poistaminen epäonnistui!\n"
                              (get-in vastaus [:response :virhe]))
                         :varoitus)
    app)

  TyhjennaLomake
  (process-event [_ app]
    (-> app
        (assoc :pot2-massa-lomake nil)))

  PaivitaMurskeLomake
  (process-event [{data :data} app]
    (let [uudet-tiedot (assoc data ::pot2-domain/murskeen-nimi
                                   (materiaalin-rikastettu-nimi {:tyypit (get-in app [:materiaalikoodistot :mursketyypit])
                                                                 :materiaali data}))]
      (update app :pot2-murske-lomake merge uudet-tiedot)))

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
                          :epaonnistui ->TallennaMurskeEpaonnistui
                          :paasta-virhe-lapi? true}))
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

  PoistaMurske
  (process-event [{id :id} app]
    (tuck-apurit/post! :poista-urakan-murske
                       {:id id}
                       {:onnistui ->PoistaMurskeOnnistui
                        :epaonnistui ->PoistaMurskeEpaonnistui
                        :paasta-virhe-lapi? true})
    app)

  PoistaMurskeOnnistui
  (process-event [{massat :massat
                   murskeet :murskeet} app]
    (viesti/nayta-toast! "Murske poistettu onnistuneesti")
    (hae-massat-ja-murskeet app)
    app)

  PoistaMurskeEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta-toast! (str "Murskeen poistaminen epäonnistui!\n"
                              (get-in vastaus [:response :virhe]))
                         :varoitus)
    app)

  SuljeMurskeLomake
  (process-event [_ app]
    (-> app
        (assoc :pot2-murske-lomake nil)))

  SuljeMateriaaliModal
  (process-event [_ app]
    (swap! nayta-materiaalikirjasto? not)
    (assoc app :tuonti-urakka nil
               :nayta-muista-urakoista-tuonti? false
               :materiaalit-toisesta-urakasta nil))

  AloitaMuokkaus
  (process-event [{lomakepolku :lomakepolku} app]
    (-> app
        (assoc-in [lomakepolku :voi-muokata?] true))))