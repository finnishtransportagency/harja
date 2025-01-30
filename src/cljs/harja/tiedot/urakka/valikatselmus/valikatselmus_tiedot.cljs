(ns harja.tiedot.urakka.valikatselmus.valikatselmus-tiedot
  (:require [tuck.core :refer [process-event] :as tuck]
            [taoensso.encore :refer [dissoc-in] :as encore]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.domain.urakka :as urakka]
            [harja.domain.kulut.valikatselmus :as valikatselmus]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]))

(def valikatselmus-nakymassa? (atom false))

;; Oikaisut
(defrecord TallennaOikaisu [oikaisu id])
(defrecord TallennaOikaisuOnnistui [vastaus id])
(defrecord TallennaOikaisuEpaonnistui [vastaus])
(defrecord PoistaOikaisu [oikaisu id])
(defrecord PoistaOikaisuOnnistui [vastaus id])
(defrecord PoistaOikaisuEpaonnistui [vastaus])
(defrecord PaivitaTavoitehinnanOikaisut [hoitokauden-alkuvuosi uusi])

;; Kattohinnan oikaisut
(defrecord KattohinnanOikaisuaMuokattu [kattohinta])
(defrecord TallennaKattohinnanOikaisu [])
(defrecord TallennaKattohinnanOikaisuOnnistui [vastaus id])
(defrecord TallennaKattohinnanOikaisuEpaonnistui [vastaus])
(defrecord PoistaKattohinnanOikaisu [])
(defrecord PoistaKattohinnanOikaisuOnnistui [vastaus id])
(defrecord PoistaKattohinnanOikaisuEpaonnistui [vastaus])
(defrecord KattohinnanMuokkaaPainettu [kattohinta])

;; Päätökset
(defrecord NollaaPaatoksetJosUrakkaVaihtui [])
(defrecord PaivitaPaatosLomake [tiedot paatos])
(defrecord TallennaPaatos [paatos])
(defrecord TallennaPaatosOnnistui [vastaus tyyppi uusi?])
(defrecord TallennaPaatosEpaonnistui [vastaus])
(defrecord PoistaPaatos [id tyyppi])
(defrecord PoistaPaatosOnnistui [vastaus tyyppi])
(defrecord PoistaPaatosEpaonnistui [vastaus])
(defrecord MuokkaaPaatosta [lomake-avain])
(defrecord AlustaPaatosLomakkeet [paatokset hoitokauden-alkuvuosi])
(defrecord PaivitaMaksunTyyppi [tyyppi])
(defrecord PoistaLupausPaatos [id])
(defrecord PoistaLupausPaatosOnnistui [vastaus])
(defrecord PoistaLupausPaatosEpaonnistui [vastaus])
(defrecord TallennaTavoitehinnanMuutosPaatos [paatos])
(defrecord PoistaTavoitehinnanMuutosPaatos [paatos-id])
(defrecord TallennaHintaPaatos [paatos])
(defrecord PoistaHintaPaatos [paatos-id])
(defrecord PaivitaKattohinnanSiirtoCheckbox [uusi-arvo])
(defrecord PaivitaKattohinnanSiirtoMaara [uusi-arvo])


(defrecord ValitseHoitokausi [urakkaid vuosi])
(defrecord HaeValikatselmuksenTiedotOnnistui [vastaus])
(defrecord HaeValikatselmuksenTiedotEpaonnistui [vastaus])

;; Hae Välikatselmuksen tiedot
(defrecord HaeValikatselmuksenTiedot [urakkaid hoitovuosi])

(defrecord AvaaPaatos [avain])

(def tyyppi->lomake
  {::valikatselmus/kattohinnan-ylitys :kattohinnan-ylitys-lomake
   ::valikatselmus/lupausbonus :lupausbonus-lomake
   ::valikatselmus/lupaussanktio :lupaussanktio-lomake})

(defn nollaa-paatokset [app]
  (-> app
    ;; Nollaa päätökset
    (assoc-in [:valikatselmuksen-tiedot :urakan-paatokset] nil)
    ;; Nollataan kattohinnan ylitys-lomake
    (assoc-in [:valikatselmuksen-tiedot :kattohinnan-ylitys-lomake] {})))

(defn filtteroi-paatos [hoitokauden-alkuvuosi tyyppi paatokset]
  (first (filter #(and
                    (or (= (name tyyppi) (::valikatselmus/tyyppi %))
                      (= (name (keyword tyyppi)) (::valikatselmus/tyyppi %)))
                    (= hoitokauden-alkuvuosi (::valikatselmus/hoitokauden-alkuvuosi %)))
           paatokset)))

(defn alusta-paatos-lomakkeet [paatokset hoitokauden-alkuvuosi]
  (let [filtteroi-paatos (fn [tyyppi]
                           (filtteroi-paatos hoitokauden-alkuvuosi tyyppi paatokset))
        kattohinnan-ylitys (filtteroi-paatos ::valikatselmus/kattohinnan-ylitys)
        lupausbonus (filtteroi-paatos ::valikatselmus/lupausbonus)
        lupaussanktio (filtteroi-paatos ::valikatselmus/lupaussanktio)]
    {:kattohinnan-ylitys-lomake (if (some? kattohinnan-ylitys)
                                  {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id kattohinnan-ylitys)
                                   :maksun-tyyppi (cond (and
                                                          (pos? (::valikatselmus/urakoitsijan-maksu kattohinnan-ylitys))
                                                          (pos? (::valikatselmus/siirto kattohinnan-ylitys))) :osa
                                                    (pos? (::valikatselmus/siirto kattohinnan-ylitys)) :siirto
                                                    :else :maksu)
                                   :siirto (when (pos? (::valikatselmus/siirto kattohinnan-ylitys)) (::valikatselmus/siirto kattohinnan-ylitys))}
                                  {})
     :lupausbonus-lomake (when (not (nil? lupausbonus))
                           {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id lupausbonus)})
     :lupaussanktio-lomake (when (not (nil? lupaussanktio))
                             {::valikatselmus/paatoksen-id (::valikatselmus/paatoksen-id lupaussanktio)})}))

(defn poista-kattohinnan-oikaisu [app]
  (tuck-apurit/post! app :poista-kattohinnan-oikaisu
    {::urakka/id (-> @tila/yleiset :urakka :id)
     ::valikatselmus/hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)}
    {:onnistui ->PoistaKattohinnanOikaisuOnnistui
     :epaonnistui ->PoistaKattohinnanOikaisuEpaonnistui
     :paasta-virhe-lapi? true}))

(defn hae-valikatselmuksen-tiedot [urakkaid hoitovuosi]
  (tuck-apurit/post! :hae-valikatselmuksen-tiedot-hoitovuodelle
    {:urakkaid urakkaid
     :hoitovuosi hoitovuosi}
    {:onnistui ->HaeValikatselmuksenTiedotOnnistui
     :epaonnistui ->HaeValikatselmuksenTiedotEpaonnistui}))

(extend-protocol tuck/Event
  ;; Tavoitehinnan oikaisut
  TallennaOikaisu
  (process-event [{oikaisu :oikaisu id :id} app]
    (let [oikaisu (merge {::urakka/id (-> @tila/yleiset :urakka :id)
                          ::valikatselmus/hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)}
                    oikaisu)]
      ;; Lähetetään oikaisun tallennus serverille vain, jos kaikki tiedot on syötetty
      (when (and (::valikatselmus/otsikko oikaisu)
              (::valikatselmus/selite oikaisu)
              (::valikatselmus/summa oikaisu)
              (::valikatselmus/hoitokauden-alkuvuosi oikaisu)
              (::urakka/id oikaisu))
        (tuck-apurit/post! :tallenna-tavoitehinnan-oikaisu
          oikaisu
          {:onnistui ->TallennaOikaisuOnnistui
           :onnistui-parametrit [id]
           :epaonnistui ->TallennaOikaisuEpaonnistui
           :paasta-virhe-lapi? true})))
    app)

  TallennaOikaisuOnnistui
  (process-event [{vastaus :vastaus id :id} {:keys [hoitokauden-alkuvuosi tavoitehinnan-oikaisut] :as app}]
    (let [_ (js/console.log "TallennaOikaisuOnnistui" (pr-str vastaus))]
      (viesti/nayta-toast! "Oikaisu tallennettu")
      ;; Haetaan välikatselmuksen tiedot uusiksi
      (hae-valikatselmuksen-tiedot (-> @tila/yleiset :urakka :id) hoitokauden-alkuvuosi)
      (nollaa-paatokset app)))

  TallennaOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Oikaisun tallennuksessa tapahtui virhe" :varoitus)
    app)

  PoistaOikaisu
  (process-event [{oikaisu :oikaisu id :id} app]
    (if (not (::valikatselmus/oikaisun-id oikaisu))
      (assoc-in app [:valikatselmuksen-tiedot :tavoitehinnan-oikaisut (:hoitokauden-alkuvuosi app) id :poistettu] true)
      (tuck-apurit/post! app :poista-tavoitehinnan-oikaisu
        oikaisu
        {:onnistui ->PoistaOikaisuOnnistui
         :epaonnistui ->PoistaOikaisuEpaonnistui
         :onnistui-parametrit [id]
         :paasta-virhe-lapi? true}))
    app)

  PoistaOikaisuOnnistui
  (process-event [{vastaus :vastaus id :id} app]
    (do
      (viesti/nayta-toast! "Oikaisu poistettu")
      (hae-valikatselmuksen-tiedot (-> @tila/yleiset :urakka :id) (:hoitokauden-alkuvuosi app))
      (-> app
        (assoc-in [:valikatselmuksen-tiedot :tavoitehinnan-oikaisut (:hoitokauden-alkuvuosi app) id :poistettu] true)
        (nollaa-paatokset))))

  PoistaOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PoistaOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Oikaisun poistamisessa tapahtui virhe" :varoitus)
    app)

  PaivitaTavoitehinnanOikaisut
  (process-event [{hoitokauden-alkuvuosi :hoitokauden-alkuvuosi uusi :uusi} app]
    (assoc-in app [:valikatselmuksen-tiedot :tavoitehinnan-oikaisut hoitokauden-alkuvuosi] uusi))

  ;; Kattohinnan oikaisut

  KattohinnanOikaisuaMuokattu
  (process-event [{kattohinta :kattohinta} app]
    (assoc-in app [:valikatselmuksen-tiedot :kattohinnan-oikaisu :uusi-kattohinta] kattohinta))

  TallennaKattohinnanOikaisu
  (process-event [_ {{uusi-kattohinta :uusi-kattohinta} :kattohinnan-oikaisu :as app}]
    (if uusi-kattohinta
      (let [oikaisu {::urakka/id (-> @tila/yleiset :urakka :id)
                     ::valikatselmus/hoitokauden-alkuvuosi (:hoitokauden-alkuvuosi app)
                     ::valikatselmus/uusi-kattohinta uusi-kattohinta}]
        (tuck-apurit/post! :tallenna-kattohinnan-oikaisu
          oikaisu
          {:onnistui ->TallennaKattohinnanOikaisuOnnistui
           :epaonnistui ->TallennaKattohinnanOikaisuEpaonnistui
           :paasta-virhe-lapi? true}))
      ;; Jos kattohinta-kenttä on tyhjä, poista kattohinnan oikaisu
      (poista-kattohinnan-oikaisu app))
    app)

  TallennaKattohinnanOikaisuOnnistui
  (process-event [{vastaus :vastaus} {:keys [hoitokauden-alkuvuosi] :as app}]
    (viesti/nayta-toast! "Kattohinnan oikaisu tallennettu")
    ;; Haetaan välikatselmuksen tiedot uusiksi
    (hae-valikatselmuksen-tiedot (-> @tila/yleiset :urakka :id) (:hoitokauden-alkuvuosi app))
    (->
      app
      (assoc-in [:valikatselmuksen-tiedot :kattohintojen-oikaisut hoitokauden-alkuvuosi] vastaus)
      (dissoc-in [:valikatselmuksen-tiedot] :kattohinnan-oikaisu)
      (nollaa-paatokset)))

  TallennaKattohinnanOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaKattohinnanOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Kattohinnan oikaisun tallennuksessa tapahtui virhe" :varoitus)
    app)

  PoistaKattohinnanOikaisu
  (process-event [_ app]
    (poista-kattohinnan-oikaisu app)
    app)

  PoistaKattohinnanOikaisuOnnistui
  (process-event [{vastaus :vastaus} app]
    (do
      (viesti/nayta-toast! "Kattohinnan oikaisu poistettu")
      (hae-valikatselmuksen-tiedot (-> @tila/yleiset :urakka :id) (:hoitokauden-alkuvuosi app))
      (->
        app
        (update-in [:valikatselmuksen-tiedot :kattohintojen-oikaisut] dissoc (:hoitokauden-alkuvuosi app))
        (dissoc-in app [:valikatselmuksen-tiedot] :kattohinnan-oikaisu)
        (nollaa-paatokset))))

  PoistaKattohinnanOikaisuEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PoistaKattohinnanOikaisuEpaonnistui" vastaus)
    (viesti/nayta-toast! "Kattohinnan oikaisun poistamisessa tapahtui virhe" :varoitus)
    app)

  KattohinnanMuokkaaPainettu
  (process-event [{kattohinta :kattohinta} app]
    (-> app
      (assoc-in [:valikatselmuksen-tiedot :kattohinnan-oikaisu :muokkaa-painettu?] true)
      (assoc-in [:valikatselmuksen-tiedot :kattohinnan-oikaisu :uusi-kattohinta] kattohinta)))

  AlustaPaatosLomakkeet
  (process-event [{paatokset :paatokset hoitokauden-alkuvuosi :hoitokauden-alkuvuosi} app]
    (let [;; Tyhjennetään vanhat lomakkeet
          {kattohinnan-ylitys-lomake :kattohinnan-ylitys-lomake
           lupausbonus-lomake :lupausbonus-lomake
           lupaussanktio-lomake :lupaussanktio-lomake} (alusta-paatos-lomakkeet paatokset hoitokauden-alkuvuosi)]
      (cond-> app
        kattohinnan-ylitys-lomake (assoc-in [:valikatselmuksen-tiedot :kattohinnan-ylitys-lomake] kattohinnan-ylitys-lomake)
        lupausbonus-lomake (assoc-in [:valikatselmuksen-tiedot :lupausbonus-lomake] lupausbonus-lomake)
        lupaussanktio-lomake (assoc-in [:valikatselmuksen-tiedot :lupaussanktio-lomake] lupaussanktio-lomake))))

  NollaaPaatoksetJosUrakkaVaihtui
  (process-event [_ app]
    (if (not= (:valittu-urakka app) @nav/valittu-urakka-id)
      (-> app
        (nollaa-paatokset)
        (assoc :valittu-urakka @nav/valittu-urakka-id))
      app))

  PaivitaPaatosLomake
  (process-event [{tiedot :tiedot paatos :paatos} app]
    (assoc-in app [:valikatselmuksen-tiedot paatos] tiedot))

  TallennaPaatos
  (process-event [{paatos :paatos} app]
    (tuck-apurit/post! :tallenna-urakan-paatos
      paatos
      {:onnistui ->TallennaPaatosOnnistui
       :onnistui-parametrit [(::valikatselmus/tyyppi paatos)
                             (nil? (::valikatselmus/paatoksen-id paatos))]
       :epaonnistui ->TallennaPaatosEpaonnistui})
    (assoc app :tallennus-kesken? true))

  TallennaPaatosOnnistui
  (process-event [{tyyppi :tyyppi vastaus :vastaus uusi? :uusi?} {:keys [urakan-paatokset] :as app}]
    (viesti/nayta-toast! "Päätöksen tallennus onnistui")
    (let [paivitetyt-paatokset (map #(if (= (select-keys % [::valikatselmus/tyyppi ::valikatselmus/hoitokauden-alkuvuosi])
                                           (select-keys vastaus [::valikatselmus/tyyppi ::valikatselmus/hoitokauden-alkuvuosi]))
                                       vastaus
                                       %)
                                 urakan-paatokset)
          paivitetyt-paatokset (if uusi? (conj paivitetyt-paatokset vastaus)
                                 paivitetyt-paatokset)]
      ;; Jos tallennettiin lupauspäätös, niin joudutaan hakemaan tiedot uusiksi.
      (hae-valikatselmuksen-tiedot (-> @tila/yleiset :urakka :id) (:hoitokauden-alkuvuosi app))
      (-> app
        (assoc-in [:valikatselmuksen-tiedot :urakan-paatokset] paivitetyt-paatokset)
        (assoc-in [:valikatselmuksen-tiedot (tyyppi tyyppi->lomake) ::valikatselmus/paatoksen-id] (::valikatselmus/paatoksen-id vastaus))
        (assoc-in [:valikatselmuksen-tiedot (tyyppi tyyppi->lomake) :muokataan?] false)
        (assoc :tallennus-kesken? false))))

  TallennaPaatosEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "TallennaPaatosEpaonnistui" vastaus)
    (viesti/nayta-toast! "Päätöksen tallennuksessa tapahtui virhe" :varoitus)
    (assoc app :tallennus-kesken? false))

  PoistaPaatos
  (process-event [{id :id tyyppi :tyyppi} app]
    (tuck-apurit/post! :poista-paatos
      {::valikatselmus/paatoksen-id id}
      {:onnistui ->PoistaPaatosOnnistui
       :onnistui-parametrit [tyyppi]
       :epaonnistui ->PoistaPaatosEpaonnistui})
    (assoc app :tallennus-kesken? true))

  PoistaPaatosOnnistui
  (process-event [{tyyppi :tyyppi} app]
    (hae-valikatselmuksen-tiedot (-> @tila/yleiset :urakka :id) (:hoitokauden-alkuvuosi app))
    (-> app
      (assoc :tallennus-kesken? false)
      (update-in [:valikatselmuksen-tiedot (tyyppi->lomake tyyppi)] dissoc ::valikatselmus/paatoksen-id)))

  PoistaPaatosEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PoistaPaatosEpaonnistui" vastaus)
    (viesti/nayta-toast! "Päätöksen kumoamisessa tapahtui virhe" :varoitus)
    (assoc app :tallennus-kesken? false))

  MuokkaaPaatosta
  (process-event [{lomake-avain :lomake-avain} app]
    (assoc-in app [:valikatselmuksen-tiedot lomake-avain :muokataan?] true))

  PaivitaMaksunTyyppi
  (process-event [{tyyppi :tyyppi} app]
    (assoc-in app [:valikatselmuksen-tiedot :kattohinnan-ylitys-lomake :maksun-tyyppi] tyyppi))

  PoistaLupausPaatos
  (process-event [{id :id} app]
    (tuck-apurit/post! :poista-paatos
      {::valikatselmus/paatoksen-id id}
      {:onnistui ->PoistaLupausPaatosOnnistui
       :epaonnistui ->PoistaLupausPaatosEpaonnistui})
    (assoc app :tallennus-kesken? true))

  PoistaLupausPaatosOnnistui
  (process-event [{vastaus :vastaus} app]
    (hae-valikatselmuksen-tiedot (-> @tila/yleiset :urakka :id) (:hoitokauden-alkuvuosi app))
    (viesti/nayta-toast! "Päätöksen poisto onnistui!")
    (assoc app :tallennus-kesken? false))

  PoistaLupausPaatosEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.warn "PoistaLupausPaatosEpaonnistui" vastaus)
    (viesti/nayta-toast! "Päätöksen poistossa tapahtui virhe" :varoitus)
    (assoc app :tallennus-kesken? false))

  ValitseHoitokausi
  (process-event [{urakkaid :urakkaid vuosi :vuosi} app]
    (let [app (-> app
                (assoc :valittu-kuukausi nil)
                ;; Lupaukset on kiinteässä linkissä kustannusten seurannan kanssa joten tarvitaan hoitokaudellekin sama avain
                (assoc :valittu-hoitokausi [(pvm/hoitokauden-alkupvm vuosi)
                                            (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                (assoc :nykyhetki (pvm/nyt))
                (assoc :haku-kaynnissa? true)
                (assoc :hoitokauden-alkuvuosi vuosi))]
      ;; Haetaan kaikki välikatselmuksessa tarvittavat tiedot
      (hae-valikatselmuksen-tiedot (-> @tila/yleiset :urakka :id) (:hoitokauden-alkuvuosi app))
      (assoc app :haku-kaynnissa? true)))

  HaeValikatselmuksenTiedot
  (process-event [{urakkaid :urakkaid hoitovuosi :hoitovuosi} app]
    (hae-valikatselmuksen-tiedot urakkaid hoitovuosi)
    (assoc app :haku-kaynnissa? true))

  HaeValikatselmuksenTiedotOnnistui
  (process-event [{vastaus :vastaus} app]
    (-> app
      (assoc :paatokset (:paatokset vastaus))
      (assoc :tavoitehinnan-muutokset (:tavoitehinnan-muutokset vastaus))
      (assoc :haku-kaynnissa? false)))

  HaeValikatselmuksenTiedotEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (js/console.log "HaeValikatselmuksenTiedotEpaonnistui :: vastaus" (pr-str vastaus))
    (-> app
      (assoc :valikatselmuksen-tiedot nil)
      (assoc :haku-kaynnissa? false)))

  ;; Monta paatosta voi olla avattuna kerrallaan
  AvaaPaatos
  (process-event [{avain :avain} app]
    (let [_ (js/console.log "AvaaPaatos :: avain " (pr-str avain) "avatus päätökset: " (pr-str (:avatut-paatokset app)))
          app (if (nil? (:avatut-paatokset app))
                (assoc app :avatut-paatokset #{})
                app)]
      (if (contains? (:avatut-paatokset app) avain)
        (assoc app :avatut-paatokset (disj (:avatut-paatokset app) avain))
        (assoc app :avatut-paatokset (merge (:avatut-paatokset app) avain)))))

  PaivitaKattohinnanSiirtoCheckbox
  (process-event [{uusi-arvo :uusi-arvo} app]
    (js/console.log "PaivitaKattohinnanSiirtoCheckbox :: uusi-arvo:" (pr-str uusi-arvo))
    (assoc-in app [:paatokset :tavoitehinta-ylitys :siirra?] uusi-arvo))
  )


(defn avaa-tai-sulje-haitari [avain])
