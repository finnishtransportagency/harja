(ns harja.tiedot.hallinta.lupaukset-tiedot
  (:require [cljs.core.async :refer [>! <!]]
            [harja.loki :as log]
            [harja.ui.viesti :as viesti]
            [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; Apufunktio varianssin lisäämiseen
(defn- lisaa-varianssi
  "Lisää arvolle satunnaisen varianssin kuukauden mukaan.
   Varianssi on suurempi helpoilla kuukausilla (lokakuu, elokuu) joiden raja-arvot ovat löysät,
   ja pienempi vaikeilla kuukausilla (kesäkuu) joiden raja-arvot ovat tiukat.
   
   Kuukausikohtaiset varianssit:
   - Kesäkuu (6): ±8% (vaikein, raja 1-2%)
   - Huhtikuu (4): ±12% (vaikea, raja 2-3%)
   - Tammikuu (1): ±18% (kohtalainen, raja 4-6%)
   - Lokakuu (10) ja Elokuu (8): ±25% (helpoimmat, raja 7-9%)"
  [arvo kuukausi]
  (let [varianssi-leveys (case kuukausi
                           6  0.08   ;; Kesäkuu: ±8% (vaikein, raja alle 1% → 8p)
                           4  0.12   ;; Huhtikuu: ±12% (vaikea, raja alle 2% → 8p)
                           1  0.18   ;; Tammikuu: ±18% (kohtalainen, raja alle 4% → 8p)
                           (10 8) 0.25  ;; Lokakuu ja Elokuu: ±25% (helpoimmat, raja alle 7% → 8p)
                           0.18)      ;; Default muille kuukausille
        varianssi-prosentti (+ (- 1.0 varianssi-leveys) 
                              (* (js/Math.random) (* 5 varianssi-leveys)))]
    (js/Math.round (* arvo varianssi-prosentti))))


(defrecord HaeLupaustenLinkitykset [])
(defrecord HaeLupaustenLinkityksetOnnistui [vastaus])
(defrecord HaeLupaustenLinkityksetEpaonnistui [vastaus])

(defrecord HaeLupaustenKategoriat [])
(defrecord HaeLupaustenKategoriatOnnistui [vastaus])
(defrecord HaeLupaustenKategoriatEpaonnistui [vastaus])

(defrecord ValitseKategoria [kategoria])
(defrecord ValitseKategoriaOnnistui [vastaus])
(defrecord ValitseKategoriaEpaonnistui [vastaus])

(defrecord ValitseUrakka [urakka-id])
(defrecord ValitseUrakkaOnnistui [vastaus])
(defrecord ValitseUrakkaEpaonnistui [vastaus])

;; Testaustyökalut
(defrecord AvaaTestausosio [])
(defrecord SuljeTestausosio [])
(defrecord HaeUrakatTestaukseenOnnistui [vastaus])
(defrecord ValitseTestausUrakka [urakka])
(defrecord ValitseTestausHoitokausi [hoitokausi])
(defrecord HaeTestausdata [])
(defrecord HaeTestausdataOnnistui [vastaus])
(defrecord HaeTestausdataEpaonnistui [vastaus])
(defrecord PaivitaTestausparametri [avain arvo])
(defrecord ValitseKustannusennuste [kustannusennuste])
(defrecord TriggerLaskenta [])
(defrecord TriggerLaskentaOnnistui [vastaus])
(defrecord TriggerLaskentaEpaonnistui [vastaus])
(defrecord TaytaKustannusennusteetTestaukseen [])
(defrecord HaeMaarapaivat [])
(defrecord HaeMaarapaivatOnnistui [vastaus])
(defrecord TallennaYksiKustannusennuste [index kuukausi vuosi lupaus-id])
(defrecord TallennaYksiKustannusennusteOnnistui [index])
(defrecord TaytaKustannusennusteetOnnistui [vastaus])
(defrecord TaytaKustannusennusteetEpaonnistui [vastaus])
(defrecord PoistaKustannusennusteetTestaukseen [])
(defrecord PoistaKustannusennusteetOnnistui [vastaus])
(defrecord PoistaKustannusennusteetEpaonnistui [vastaus])

(def tila (atom {:valittu-urakka nil
                 :valittu-kategoria nil
                 :lupausten-linkitykset []
                 :haku-kaynnissa? false
                 ;; Testaustyökalut
                 :testaus-auki? false
                 :testaus-urakat []
                 :testaus-valittu-urakka nil
                 :testaus-valittu-hoitokausi nil
                 :testaus-data nil
                 :testaus-parametrit {:toteutunut-tavoitehinta 1000000
                                      :toteutunut-kustannus 950000}
                 :laskenta-kaynnissa? false
                 :tee-taytto-kaynnissa? false
                 :taytto-edistyminen {:yhteensa 0 :valmis 0}}))

(extend-protocol tuck/Event
  HaeLupaustenLinkitykset
  (process-event [_ app]
    (tuck-apurit/post! :hae-lupausten-linkitykset
      {}
      {:onnistui ->HaeLupaustenLinkityksetOnnistui
       :epaonnistui ->HaeLupaustenLinkityksetEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  HaeLupaustenLinkityksetOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :lupausten-linkitykset vastaus))

  HaeLupaustenLinkityksetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Urakoiden linkityksien haku epäonnistui" :varoitus)
    app)

  HaeLupaustenKategoriat
  (process-event [_ app]
    (tuck-apurit/post! :hae-rivin-tunnistin-selitteet
      {}
      {:onnistui ->HaeLupaustenKategoriatOnnistui
       :epaonnistui ->HaeLupaustenKategoriatEpaonnistui
       :paasta-virhe-lapi? true})
    app)

  HaeLupaustenKategoriatOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :lupausten-kategoriat vastaus))

  HaeLupaustenKategoriatEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Urakoiden kategorioiden haku epäonnistui" :varoitus)
    app)

  ValitseKategoria
  (process-event [{:keys [kategoria]} app]
    (tuck-apurit/post! :hae-kategorian-urakat
      {:kategoria kategoria}
      {:onnistui ->ValitseKategoriaOnnistui
       :epaonnistui ->ValitseKategoriaEpaonnistui
       :paasta-virhe-lapi? true})
    (-> app
      (assoc :haku-kaynnissa? true)
      (assoc :valittu-kategoria kategoria)))

  ValitseKategoriaOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :kategorian-urakat vastaus)))

  ValitseKategoriaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Urakoiden haku epäonnistui" :varoitus)
    (assoc app :haku-kaynnissa? false))

  ValitseUrakka
  (process-event [{:keys [urakka-id]} app]
    (tuck-apurit/post! :hae-urakan-lupaukset
      {:urakka-id (:urakka_id urakka-id)}
      {:onnistui ->ValitseUrakkaOnnistui
       :epaonnistui ->ValitseUrakkaEpaonnistui
       :paasta-virhe-lapi? true})
    (-> app
      (assoc :haku-kaynnissa? true)
      (assoc :valittu-urakka urakka-id)))

  ValitseUrakkaOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :urakan-lupaukset vastaus)))

  ValitseUrakkaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Lupausten haku epäonnistui" :varoitus)
    (assoc app :haku-kaynnissa? false))

  ;; Testaustyökalut
  AvaaTestausosio
  (process-event [_ app]
    (tuck-apurit/post! :hae-urakat-kustannusennuste-testaukseen
      {}
       {:onnistui ->HaeUrakatTestaukseenOnnistui
        :epaonnistui (fn [_] 
                       (viesti/nayta-toast! "Urakoiden haku epäonnistui" :varoitus))
       :paasta-virhe-lapi? true})
    (assoc app :testaus-auki? true))

  SuljeTestausosio
  (process-event [_ app]
    (assoc app :testaus-auki? false))

  HaeUrakatTestaukseenOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :testaus-urakat (:urakat vastaus)))

  ValitseTestausUrakka
  (process-event [{:keys [urakka]} app]
    (-> app
      (assoc :testaus-valittu-urakka urakka)
      (assoc :testaus-valittu-hoitokausi nil)
      (assoc :testaus-data nil)))

  ValitseTestausHoitokausi
  (process-event [{:keys [hoitokausi]} app]
    (let [urakka-id (:urakka-id (:testaus-valittu-urakka app))]
      (when (and urakka-id hoitokausi)
        (tuck-apurit/post! :hae-kustannusennuste-testausdata
          {:urakka-id urakka-id
           :hoitokauden-alkuvuosi hoitokausi}
          {:onnistui ->HaeTestausdataOnnistui
           :epaonnistui ->HaeTestausdataEpaonnistui
           :paasta-virhe-lapi? true}))
      (assoc app :testaus-valittu-hoitokausi hoitokausi)))

  HaeTestausdata
  (process-event [_ app]
    (let [urakka-id (:urakka-id (:testaus-valittu-urakka app))
          hoitokausi (:testaus-valittu-hoitokausi app)]
      (when (and urakka-id hoitokausi)
        (tuck-apurit/post! :hae-kustannusennuste-testausdata
          {:urakka-id urakka-id
           :hoitokauden-alkuvuosi hoitokausi}
          {:onnistui ->HaeTestausdataOnnistui
           :epaonnistui ->HaeTestausdataEpaonnistui
           :paasta-virhe-lapi? true}))
      app))

  HaeTestausdataOnnistui
  (process-event [{:keys [vastaus]} app]
    (assoc app :testaus-data vastaus))

  HaeTestausdataEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Testausdatan haku epäonnistui" :varoitus)
    app)

  PaivitaTestausparametri
  (process-event [{:keys [avain arvo]} app]
    (assoc-in app [:testaus-parametrit avain] arvo))

  ValitseKustannusennuste
  (process-event [{:keys [kustannusennuste]} app]
    (assoc app :valittu-kustannusennuste kustannusennuste))

  TriggerLaskenta
  (process-event [_ app]
    (let [urakka-id (:urakka-id (:testaus-valittu-urakka app))
          hoitokausi (:testaus-valittu-hoitokausi app)
          parametrit (:testaus-parametrit app)]
      (when (and urakka-id hoitokausi)
        (tuck-apurit/post! :triggeroi-kustannusennuste-laskenta
          {:urakka-id urakka-id
           :hoitokauden-alkuvuosi hoitokausi
           :toteutunut-tavoitehinta (:toteutunut-tavoitehinta parametrit)
           :toteutunut-kustannus (:toteutunut-kustannus parametrit)}
          {:onnistui ->TriggerLaskentaOnnistui
           :epaonnistui ->TriggerLaskentaEpaonnistui
           :paasta-virhe-lapi? true}))
      (assoc app :laskenta-kaynnissa? true)))

  TriggerLaskentaOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Laskenta suoritettu onnistuneesti!" :onnistui)
    (-> app
      (assoc :laskenta-kaynnissa? false)
      (assoc :testaus-data vastaus)))

  TriggerLaskentaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Laskenta epäonnistui" :varoitus)
    (assoc app :laskenta-kaynnissa? false))

  TaytaKustannusennusteetTestaukseen
  (process-event [_ app]
    (let [testaus-data (:testaus-data app)
          lupaus-id (:lupaus-id testaus-data)
          urakka-id (:urakka-id (:testaus-valittu-urakka app))
          hoitokausi (:testaus-valittu-hoitokausi app)
          parametrit (:testaus-parametrit app)
          tavoitehinta (:toteutunut-tavoitehinta parametrit)
          kustannus (:toteutunut-kustannus parametrit)]
      
      (cond
        (not lupaus-id)
        (do
          (log/warn "Lupaus-id puuttuu! Urakalla ei ole kustannusennuste-lupausta.")
          (log/warn "Tarkista että valitsit urakan jolla on kustannusennuste-lupaus määriteltynä.")
          (viesti/nayta-toast! 
            "Valitulla urakalla ei ole kustannusennuste-lupausta - valitse toinen urakka" 
            :varoitus)
          app)
        
        (not urakka-id)
        (do
          (log/warn "Urakka-id puuttuu!")
          (viesti/nayta-toast! "Valitse ensin urakka" :varoitus)
          app)
        
        (not hoitokausi)
        (do
          (log/warn "Hoitokausi puuttuu!")
          (viesti/nayta-toast! "Valitse hoitokausi" :varoitus)
          app)
        
        (not tavoitehinta)
        (do
          (log/warn "Tavoitehinta puuttuu!")
          (viesti/nayta-toast! "Aseta toteutunut tavoitehinta" :varoitus)
          app)
        
        (not kustannus)
        (do
          (log/warn "Kustannus puuttuu!")
          (viesti/nayta-toast! "Aseta toteutunut kustannus" :varoitus)
          app)
        
        :else
        (do
          ;; Hae määräpäivät ja aloita täyttö
          (tuck-apurit/post! :hae-kustannusennuste-maarapaivat
            {:lupaus-id lupaus-id
             :hoitokauden-alkuvuosi hoitokausi
             :urakka-id urakka-id}
            {:onnistui ->HaeMaarapaivatOnnistui
             :epaonnistui ->TaytaKustannusennusteetEpaonnistui
             :paasta-virhe-lapi? true})
          (assoc app :tee-taytto-kaynnissa? true
                     :taytto-edistyminen {:yhteensa 0 :valmis 0})))))

  HaeMaarapaivatOnnistui
  (process-event [{:keys [vastaus]} app]
    ;; Määräpäivät haettu, aloita kustannusennusteiden luonti
    (let [maarapaivat (:maarapaivat vastaus)  ;; Backend palauttaa {:maarapaivat [...]}
          urakka-id (:urakka-id (:testaus-valittu-urakka app))
          lupaus-id (:lupaus-id (:testaus-data app))
          parametrit (:testaus-parametrit app)
          base-tavoitehinta (:toteutunut-tavoitehinta parametrit)
          base-kustannus (:toteutunut-kustannus parametrit)]
      
      ;; Tallenna jokainen kustannusennuste erikseen
      (doseq [[index {:keys [kuukausi vuosi]}] (map-indexed vector maarapaivat)]
        (let [tavoitehinta-varianssilla (lisaa-varianssi base-tavoitehinta kuukausi)
              kustannukset-varianssilla (lisaa-varianssi base-kustannus kuukausi)]
          
          (tuck-apurit/post! :vastaa-lupaukseen
            {:lupaus-id lupaus-id
             :urakka-id urakka-id
             :kuukausi kuukausi
             :vuosi vuosi
             :paatos false  ;; Ei päätös, vaan normaali kustannusennuste-kirjaus
             :kustannusennuste {:tavoitehinta tavoitehinta-varianssilla
                               :toteutuneet-kustannukset kustannukset-varianssilla}}
            {:onnistui (fn [_] (->TallennaYksiKustannusennusteOnnistui index))
             :epaonnistui ->TaytaKustannusennusteetEpaonnistui
             :paasta-virhe-lapi? true})))
      
      (assoc app :taytto-edistyminen {:yhteensa (count maarapaivat) 
                                  :valmis 0})))

  TallennaYksiKustannusennusteOnnistui
  (process-event [{:keys [_index]} app]
    (let [progress (:taytto-edistyminen app)
          valmis (inc (:valmis progress))
          yhteensa (:yhteensa progress)]
      
      ;; Jos kaikki on tallennettu, päivitä data
      (if (= valmis yhteensa)
        (do
          (viesti/nayta-toast! 
            (str "Luotiin " yhteensa " kustannusennustetta!") 
            :onnistui)
          (let [urakka-id (:urakka-id (:testaus-valittu-urakka app))
                hoitokausi (:testaus-valittu-hoitokausi app)]
            (when (and urakka-id hoitokausi)
              (tuck-apurit/post! :hae-kustannusennuste-testausdata
                {:urakka-id urakka-id
                 :hoitokauden-alkuvuosi hoitokausi}
                {:onnistui ->HaeTestausdataOnnistui
                 :epaonnistui ->HaeTestausdataEpaonnistui
                 :paasta-virhe-lapi? true})))
          (assoc app :tee-taytto-kaynnissa? false
                     :taytto-edistyminen {:yhteensa 0 :valmis 0}))
        ;; Muuten vain päivitä progress
        (assoc app :taytto-edistyminen {:yhteensa yhteensa :valmis valmis}))))

  TaytaKustannusennusteetOnnistui
  (process-event [_ app]
    ;; Ei käytössä enää, mutta pidetään tyhjänä jos tarvitaan
    app)

  TaytaKustannusennusteetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Kustannusennusteiden täyttö epäonnistui" :varoitus)
    (assoc app :tee-taytto-kaynnissa? false))

  PoistaKustannusennusteetTestaukseen
  (process-event [_ app]
    (if (js/confirm "Haluatko varmasti poistaa kaikki hoitokauden kustannusennusteet?")
      (let [urakka-id (:urakka-id (:testaus-valittu-urakka app))
            hoitokausi (:testaus-valittu-hoitokausi app)]
        (when (and urakka-id hoitokausi)
          (tuck-apurit/post! :poista-kustannusennusteet-testaukseen
            {:urakka-id urakka-id
             :hoitokauden-alkuvuosi hoitokausi}
            {:onnistui ->PoistaKustannusennusteetOnnistui
             :epaonnistui ->PoistaKustannusennusteetEpaonnistui
             :paasta-virhe-lapi? true}))
        (assoc app :tee-taytto-kaynnissa? true))
      app))

  PoistaKustannusennusteetOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! 
      (str "Poistettiin " (:poistettu-kpl vastaus) " kustannusennustetta!") 
      :onnistui)
    ;; Päivitä data
    (let [urakka-id (:urakka-id (:testaus-valittu-urakka app))
          hoitokausi (:testaus-valittu-hoitokausi app)]
      (when (and urakka-id hoitokausi)
        (tuck-apurit/post! :hae-kustannusennuste-testausdata
          {:urakka-id urakka-id
           :hoitokauden-alkuvuosi hoitokausi}
          {:onnistui ->HaeTestausdataOnnistui
           :epaonnistui ->HaeTestausdataEpaonnistui
           :paasta-virhe-lapi? true})))
    (assoc app :tee-taytto-kaynnissa? false))

  PoistaKustannusennusteetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Kustannusennusteiden poisto epäonnistui" :varoitus)
    (assoc app :tee-taytto-kaynnissa? false)))