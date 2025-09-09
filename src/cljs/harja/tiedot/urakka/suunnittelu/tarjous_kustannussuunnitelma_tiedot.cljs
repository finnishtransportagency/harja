(ns harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot
  (:require [clojure.string :as str]
            [harja.tyokalut.yleiset :as tyokalut]
            [tuck.core :as tuck]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]
            [harja.ui.nakymasiirrin :as siirrin]
            [harja.tiedot.urakka.urakka :as tila]))

(defonce nakymassa? (atom false))
(defonce grid-tiedot-atom (atom [{}]))
(defonce grid-toimenkuvat-atom (atom [{}]))

(defn scrollaa-muutoksiin [elementin-id]
  ;; Kutsutaan kun käyttäjä generoi kuukausittaiset summat tai vahvistaa koko kustannussuunnitelman
  (siirrin/siirry-elementin-id elementin-id 200))

(defn muunna-vuodet
  "Muunnetaan UI Gridin käyttämä tietomalli bäkkärin käyttämään muotoon.
  UI Grille on oltava jokainen vuosi omassa avaimeessaa tyyliin :vuosi-2023, :vuosi-2024 jne.
  Bäkärille ne niputetaan yhteen :hoitokauden_alkuvuodet-avaimen alle, joka on lista vuosista ja summista:
  [:vuosi 2023 :summa 10.00 :vuosi 2024 :summa 20.00 ...]"
  [m]
  (let [vuosi-avaimet (filter #(re-matches #":vuosi-\d{4}" (str %)) (keys m))
        vuosisummat (mapv (fn [k]
                            (let [vuosi-numero (js/parseInt (subs (str k) 7))]
                              {:vuosi vuosi-numero :summa (get m k)}))
                      vuosi-avaimet)
        muut-avaimet (apply dissoc m vuosi-avaimet)]
    (assoc muut-avaimet :hoitovuosittaiset-arvot (vec (flatten vuosisummat)))))

;; Haetaan tarjouksen data
(defrecord HaeTarjouksenTiedot [])
(defrecord HaeTarjouksenTiedotOnnistui [vastaus])
(defrecord HaeTarjouksenTiedotEpaonnistui [vastaus])

;; Haetaan kustannussuunnitelman tiedot
(defrecord HaeKustannussuunnitelmanTiedot [])
(defrecord HaeKustannussuunnitelmanTiedotOnnistui [vastaus])
(defrecord HaeKustannussuunnitelmanTiedotEpaonnistui [vastaus])

(defrecord HaeTyhjatTarjouksenTiedot [])
(defrecord HaeTyhjatTarjouksenTiedotOnnistui [vastaus])
(defrecord HaeTyhjatTarjouksenTiedotEpaonnistui [vastaus])

;; Tallennetaan tarjouksen data
(defrecord TallennaTarjouksenTiedot [tarjous toimenkuvat])
(defrecord TallennaTarjouksenTiedotOnnistui [vastaus])
(defrecord TallennaTarjouksenTiedotEpaonnistui [vastaus])

;; Tallennetaan kilpailutettavat hankinnat kustannussuunnitelmaan
(defrecord TallennaKilpailutettavatHankinnat [kilpailutettavat-hankinnat])
(defrecord PaivitaKilpailutettavatHankinnat [kilpailutettavat-hankinnat])
(defrecord TallennaKilpailutettavatHankinnatOnnistui [vastaus])
(defrecord TallennaKilpailutettavatHankinnatEpaonnistui [vastaus])

;; Tallenna erillishankinnat
(defrecord TallennaErillishankinnat [erillishankinnat])
(defrecord PaivitaErillishankinnat [erillishankinnat])
(defrecord TallennaErillishankinnatOnnistui [vastaus])
(defrecord TallennaErillishankinnatEpaonnistui [vastaus])
(defrecord JaaErillishankinnatTasan [summa elementti])

;; Johto-ja-hallintokorvaus-käsittelyt
(defrecord TallennaJohtoJaHallintokorvaukset [johto-ja-hallintokorvaukset urakan-alkuvuosi])
(defrecord PaivitaJohtoJaHallintokorvaukset [johto-ja-hallintokorvaukset])
(defrecord PaivitaJohtoJaHallintokorvaukset2019 [johto-ja-hallintokorvaukset toimenkuva])
(defrecord TallennaJohtoJaHallintokorvauksetOnnistui [vastaus])
(defrecord TallennaJohtoJaHallintokorvauksetEpaonnistui [vastaus])
(defrecord JaaJohtoJaHallintokorvauksetTasan [summa johto-ja-hallintokorvaukset-elementti])

;; Hoidonjohtopalkkio-käsittelyt
(defrecord TallennaHoidonjohtopalkkiot [hoidonjohtopalkkiot])
(defrecord PaivitaHoidonjohtopalkkiot [hoidonjohtopalkkiot])
(defrecord TallennaHoidonjohtopalkkiotOnnistui [vastaus])
(defrecord TallennaHoidonjohtopalkkiotEpaonnistui [vastaus])
(defrecord JaaHoidonjohtopalkkiotTasan [summa hoidonjohtopalkkio-elementti])

;; Vahvistukset
(defrecord VahvistaTaiPeruutaTavoiteJaKattohinta [vahvista?])
(defrecord VahvistaTaiPeruutaTavoiteJaKattohintaOnnistui [vastaus])
(defrecord VahvistaTaiPeruutaTavoiteJaKattohintaEpaonnistui [vastaus])

(defrecord ToggleVetolaatikonMuokkaus [tila])

(defrecord ValitseHoitokausiKustannussuunnitelmaan [vuosi])
(defrecord PoistaToimenkuva [rivi])

(defn hae-kustannussuunnitelman-tiedot
  "Haetaan kustannussuunnitelman tiedot, jotta voidaan näyttää ne UI Gridissä.
  Vuosi on hoitovuoden alkuvuosi, jolle kustannussuunnitelma haetaan."
  [urakka-id vuosi]
  (tuck-apurit/post! :hae-kustannussuunnitelman-tiedot
    {:urakka-id urakka-id :hoitovuoden-alkuvuosi vuosi}
    {:onnistui ->HaeKustannussuunnitelmanTiedotOnnistui
     :epaonnistui ->HaeKustannussuunnitelmanTiedotEpaonnistui}))

(defn parsi-kilpailutettavat-hankinnat-virhe [virhe toimenpiteet]
  (let [virheen-rivi (when-let [matches (or (re-find #"\[:toimenpiteet (\d+) :loppukausi\]" virhe)
                                          (re-find #"\[:toimenpiteet (\d+) :alkukausi\]" virhe))]
                       (js/parseInt (second matches)))
        loppukausi? (str/includes? virhe ":loppukausi")
        toimenpide-rivi (nth toimenpiteet virheen-rivi)
        rivin-nimi (:nimi toimenpide-rivi)]
    (str "Rivillä " (inc virheen-rivi) ", " rivin-nimi
      (if loppukausi? "Tammi-syyskuun " "Loka-joulukuun ") "arvossa virhe. Anna positiivinen summa.")))

(defn parsi-erillishankinnat-virhe [virhe erillishankinnat]
  (let [virheen-rivi (when-let [matches (re-find #"\[:erillishankinnat (\d+) :summa\]" virhe)]
                       (js/parseInt (second matches)))
        erillishankinta (nth erillishankinnat virheen-rivi)
        rivin-nimi (:kalenterikuukausi erillishankinta)]
    (str "Rivillä " (inc virheen-rivi) ", " rivin-nimi " arvossa virhe. Anna positiivinen summa.")))

(defn parsi-hoidonjohtopalkkiot-virhe [virhe hoidonjohtopalkkiot]
  (let [virheen-rivi (when-let [matches (re-find #"\[:hoidonjohtopalkkiot (\d+) :summa\]" virhe)]
                       (js/parseInt (second matches)))
        hoidonjohtopalkkio (nth hoidonjohtopalkkiot virheen-rivi)
        rivin-nimi (:kalenterikuukausi hoidonjohtopalkkio)]
    (str "Rivillä " (inc virheen-rivi) ", " rivin-nimi " arvossa virhe. Anna positiivinen summa.")))

(defn parsi-johto-ja-hallintokorvaus-virhe [virhe johto-ja-hallintokorvaukset]
  (let [virheen-rivi (when-let [matches (re-find #"\[:johto-ja-hallintokorvaukset (\d+) :summa\]" virhe)]
                       (js/parseInt (second matches)))
        johto-ja-hallintokorvaus (nth johto-ja-hallintokorvaukset virheen-rivi)
        rivin-nimi (:kalenterikuukausi johto-ja-hallintokorvaus)]
    (str "Rivillä " (inc virheen-rivi) ", " rivin-nimi " arvossa virhe. Anna positiivinen summa.")))

(extend-protocol tuck/Event

  HaeTarjouksenTiedot
  (process-event
    [_ app]
    (tuck-apurit/post! :hae-tarjouksen-tiedot
      {:urakka-id (-> @tila/yleiset :urakka :id)}
      {:onnistui ->HaeTarjouksenTiedotOnnistui
       :epaonnistui ->HaeTarjouksenTiedotEpaonnistui})
    (-> app
      (assoc :haku-kaynnissa? true)
      (assoc :tallennus-kesken? false)))

  HaeTarjouksenTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))
      (assoc :kaikki-toimenkuvat (:kaikki-toimenkuvat vastaus))
      (assoc :urakka-id (:urakka-id vastaus))))

  HaeTarjouksenTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  HaeTyhjatTarjouksenTiedot
  (process-event
    [_ app]
    (tuck-apurit/post! :hae-tyhjat-tarjouksen-tiedot
      {:urakka-id (-> @tila/yleiset :urakka :id)}
      {:onnistui ->HaeTyhjatTarjouksenTiedotOnnistui
       :epaonnistui ->HaeTyhjatTarjouksenTiedotEpaonnistui})
    (-> app
      (assoc :haku-kaynnissa? true)
      (assoc :tallennus-kesken? false)))

  HaeTyhjatTarjouksenTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))))

  HaeTyhjatTarjouksenTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  TallennaTarjouksenTiedot
  (process-event
    [{tarjous :tarjous toimenkuvat :toimenkuvat} app]
    (let [;; Muutetaan formilta saatu tarjous oikeaan muotoon
          muunnetut-tarjousrivit (map #(muunna-vuodet %) tarjous)
          muunnetut-toimenkuvarivit (map #(muunna-vuodet %) toimenkuvat)
          tarjous (concat muunnetut-tarjousrivit muunnetut-toimenkuvarivit)
          muunnettu-tarjous {:tarjous tarjous}
          muunnettu-tarjous (assoc muunnettu-tarjous :urakka-id (-> @tila/yleiset :urakka :id))]
      (tuck-apurit/post! :tallenna-tarjouksen-tiedot
        muunnettu-tarjous
        {:onnistui ->TallennaTarjouksenTiedotOnnistui
         :epaonnistui ->TallennaTarjouksenTiedotEpaonnistui})
      (assoc app :tallennus-kesken? true)))

  TallennaTarjouksenTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :tallennus-kesken? false)
      (assoc :tarjous (:tarjous vastaus))))

  TallennaTarjouksenTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Tietojen tallentaminen epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :tallennus-kesken? false))

  HaeKustannussuunnitelmanTiedot
  (process-event
    [_ app]
    (hae-kustannussuunnitelman-tiedot (-> @tila/yleiset :urakka :id) (pvm/vuosi (first (:valittu-hoitokausi app))))
    (-> app
      (assoc :haku-kaynnissa? true)
      (assoc :tallennus-kesken? false)
      (assoc :kustannussuunnitelma [])))

  HaeKustannussuunnitelmanTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :urakan-alkuvuosi (:urakan-alkuvuosi vastaus))
      (assoc :valittu-hoitokausi (:valittu-hoitokausi vastaus))
      (assoc :tarjous (:tarjous vastaus))
      (assoc :kustannussuunnitelma (:kustannussuunnitelma vastaus))))

  HaeKustannussuunnitelmanTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  PaivitaKilpailutettavatHankinnat
  (process-event
    [{kilpailutettavat-hankinnat :kilpailutettavat-hankinnat} app]
    (let [muuttuneet (vec kilpailutettavat-hankinnat)
          ;; Laske yhteenvedot uusiksi
          muuttuneet (mapv (fn [rivi]
                             (let [alkukausi (or (:alkukausi rivi) 0)
                                   loppukausi (or (:loppukausi rivi) 0)]
                               (merge rivi
                                 {:alkukausi alkukausi
                                  :loppukausi loppukausi
                                  :alkukausi-indeksikorjattu nil
                                  :loppukausi-indeksikorjattu nil
                                  :yhteensa (+ alkukausi loppukausi)
                                  :yhteensa-indeksikorjattu nil})))
                       muuttuneet)
          yhteenveto {:nimi "Yhteensä"
                      :alkukausi (apply + (map :alkukausi muuttuneet))
                      :alkukausi-indeksikorjattu (apply + (map :alkukausi-indeksikorjattu muuttuneet))
                      :loppukausi (apply + (map :loppukausi muuttuneet))
                      :loppukausi-indeksikorjattu (apply + (map :loppukausi-indeksikorjattu muuttuneet))
                      :yhteensa (+ (apply + (map :alkukausi muuttuneet)) (apply + (map :loppukausi muuttuneet)))
                      :yhteensa-indeksikorjattu (+ (apply + (map :alkukausi-indeksikorjattu muuttuneet)) (apply + (map :loppukausi-indeksikorjattu muuttuneet)))
                      :pysyvat-muutokset "Ei muutoksia"
                      :viimeisin-muokkaus (:viimeisin-muokkaus (last (get-in app [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet])))
                      :viimeisin-muokkaaja (:viimeisin-muokkaaja (last (get-in app [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet])))}
          muuttuneet (conj muuttuneet yhteenveto)]
      (-> app
        (assoc-in [:kustannussuunnitelma :kilpailutettavat-hankinnat-virheet] nil)
        (assoc-in [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet] muuttuneet))))

  TallennaKilpailutettavatHankinnat
  (process-event
    [{kilpailutettavat-hankinnat :kilpailutettavat-hankinnat} app]
    (let [vuosi (pvm/vuosi (first (:valittu-hoitokausi app)))]
      (tuck-apurit/post! :tallenna-kilpailutettavat-hankinnat
        {:urakka-id (-> @tila/yleiset :urakka :id)
         :hoitovuoden-alkuvuosi vuosi
         :toimenpiteet kilpailutettavat-hankinnat}
        {:onnistui ->TallennaKilpailutettavatHankinnatOnnistui
         :epaonnistui ->TallennaKilpailutettavatHankinnatEpaonnistui
         :paasta-virhe-lapi? true})
      (assoc app :tallennus-kesken? true)))

  TallennaKilpailutettavatHankinnatOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Kilpailutettavat hankinat tallennettiin.")
    (-> app
      (assoc-in [:kustannussuunnitelma :kilpailutettavat-hankinnat-virheet] nil)
      (assoc :tallennus-kesken? false)
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))
      (assoc :kustannussuunnitelma (:kustannussuunnitelma vastaus))))

  TallennaKilpailutettavatHankinnatEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (let [parsitut-virheet (parsi-kilpailutettavat-hankinnat-virhe (get-in vastaus [:parse-error :original-text])
                             (get-in app [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet]))]
      (viesti/nayta-toast!
        parsitut-virheet
        :varoitus
        viesti/viestin-nayttoaika-keskipitka)
      (-> app
        (assoc-in [:kustannussuunnitelma :kilpailutettavat-hankinnat-virheet] parsitut-virheet)
        (assoc :tallennus-kesken? false))))

  PaivitaErillishankinnat
  (process-event
    [{erillishankinnat :erillishankinnat} app]
    (let [muuttuneet (sort-by (juxt :vuosi :kuukausi) (vec erillishankinnat))]
      (-> app
        (assoc-in [:kustannussuunnitelma :erillishankinnat-virheet] nil)
        (assoc-in [:kustannussuunnitelma :erillishankinnat] muuttuneet))))

  TallennaErillishankinnat
  (process-event
    [{erillishankinnat :erillishankinnat} app]
    (tuck-apurit/post! :tallenna-erillishankinnat
      {:urakka-id (-> @tila/yleiset :urakka :id)
       :hoitovuoden-alkuvuosi (pvm/vuosi (first (:valittu-hoitokausi app)))
       :erillishankinnat erillishankinnat}
      {:onnistui ->TallennaErillishankinnatOnnistui
       :epaonnistui ->TallennaErillishankinnatEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :tallennus-kesken? true))

  TallennaErillishankinnatOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Erillishankinnat tallennettiin onnistuneesti.")
    (-> app
      (assoc-in [:kustannussuunnitelma :erillishankinnat-virheet] nil)
      (assoc :tallennus-kesken? false)
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))
      (assoc :kustannussuunnitelma (:kustannussuunnitelma vastaus))))

  TallennaErillishankinnatEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (let [parsitut-virheet (parsi-erillishankinnat-virhe (get-in vastaus [:parse-error :original-text])
                             (get-in app [:kustannussuunnitelma :erillishankinnat]))]
      (viesti/nayta-toast!
        parsitut-virheet
        :varoitus
        viesti/viestin-nayttoaika-keskipitka)
      (-> app
        (assoc-in [:kustannussuunnitelma :erillishankinnat-virheet] parsitut-virheet)
        (assoc :tallennus-kesken? false))))

  JaaErillishankinnatTasan
  (process-event [{:keys [summa elementti]} app]
    (let [erillishankinnat (get-in app [:kustannussuunnitelma :erillishankinnat])
          kk-summa (tyokalut/round2 2 (/ summa 12))
          viimeneinen-summa (- summa (tyokalut/round2 2 (* 11 kk-summa)))
          erillishankinnat (map-indexed (fn [indeksi rivi]
                                          (merge rivi
                                            {:summa (if (= indeksi 11) viimeneinen-summa kk-summa)
                                             :summa_indeksikorjattu nil}))
                             erillishankinnat)]
      (scrollaa-muutoksiin elementti)
      (assoc-in app [:kustannussuunnitelma :erillishankinnat] erillishankinnat)))

  PaivitaHoidonjohtopalkkiot
  (process-event
    [{hoidonjohtopalkkiot :hoidonjohtopalkkiot} app]
    (let [muuttuneet (sort-by (juxt :vuosi :kuukausi) (vec hoidonjohtopalkkiot))]
      (-> app
        (assoc-in [:kustannussuunnitelma :hoidonjohtopalkkiot-virheet] nil)
        (assoc-in [:kustannussuunnitelma :hoidonjohtopalkkiot] muuttuneet))))

  TallennaHoidonjohtopalkkiot
  (process-event
    [{hoidonjohtopalkkiot :hoidonjohtopalkkiot} app]
    (tuck-apurit/post! :tallenna-hoidonjohtopalkkiot
      {:urakka-id (-> @tila/yleiset :urakka :id)
       :hoitovuoden-alkuvuosi (pvm/vuosi (first (:valittu-hoitokausi app)))
       :hoidonjohtopalkkiot hoidonjohtopalkkiot}
      {:onnistui ->TallennaHoidonjohtopalkkiotOnnistui
       :epaonnistui ->TallennaHoidonjohtopalkkiotEpaonnistui
       :paasta-virhe-lapi? true})
    (assoc app :tallennus-kesken? true))

  TallennaHoidonjohtopalkkiotOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Hoidonjohtopalkkiot tallennettiin.")
    (-> app
      (assoc-in [:kustannussuunnitelma :hoidonjohtopalkkiot-virheet] nil)
      (assoc :tallennus-kesken? false)
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))
      (assoc :kustannussuunnitelma (:kustannussuunnitelma vastaus))))

  TallennaHoidonjohtopalkkiotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (let [parsitut-virheet (parsi-hoidonjohtopalkkiot-virhe (get-in vastaus [:parse-error :original-text])
                             (get-in app [:kustannussuunnitelma :hoidonjohtopalkkiot]))]
      (viesti/nayta-toast!
        parsitut-virheet
        :varoitus
        viesti/viestin-nayttoaika-keskipitka)
      (-> app
        (assoc-in [:kustannussuunnitelma :hoidonjohtopalkkiot-virheet] parsitut-virheet)
        (assoc :tallennus-kesken? false))))

  JaaHoidonjohtopalkkiotTasan
  (process-event [{:keys [summa hoidonjohtopalkkio-elementti]} app]
    (let [hoidonjohtopalkkiot (get-in app [:kustannussuunnitelma :hoidonjohtopalkkiot])
          kk-summa (tyokalut/round2 2 (/ summa 12))
          viimeneinen-summa (- summa (tyokalut/round2 2 (* 11 kk-summa)))
          hoidonjohtopalkkiot (map-indexed (fn [indeksi rivi]
                                             (merge rivi
                                               {:summa (if (= indeksi 11) viimeneinen-summa kk-summa)
                                                :summa_indeksikorjattu nil}))
                                hoidonjohtopalkkiot)]
      (scrollaa-muutoksiin hoidonjohtopalkkio-elementti)
      (assoc-in app [:kustannussuunnitelma :hoidonjohtopalkkiot] hoidonjohtopalkkiot)))

  PaivitaJohtoJaHallintokorvaukset
  (process-event
    [{johto-ja-hallintokorvaukset :johto-ja-hallintokorvaukset} app]
    (let [muuttuneet (sort-by (juxt :vuosi :kuukausi) (vec johto-ja-hallintokorvaukset))]
      (-> app
        (assoc-in [:kustannussuunnitelma :johto-ja-hallintokorvaukset-virheet] nil)
        (assoc-in [:kustannussuunnitelma :johto-ja-hallintokorvaukset] muuttuneet))))

  ;; Vanhat toimenkuvat vaativat toimenkuvan kokonaissumman uudelleen laskennan
  PaivitaJohtoJaHallintokorvaukset2019
  (process-event
    [{johto-ja-hallintokorvaukset :johto-ja-hallintokorvaukset toimenkuva :toimenkuva} app]
    (let [muuttuneet (sort-by (juxt :vuosi :kuukausi) (vec johto-ja-hallintokorvaukset))
          muuttunut (filter #(= (:toimenkuva %) toimenkuva) muuttuneet)]
      (-> app
        (assoc-in [:kustannussuunnitelma :johto-ja-hallintokorvaukset-virheet] nil)
        (assoc-in [:kustannussuunnitelma :johto-ja-hallintokorvaukset] muuttuneet))))

  TallennaJohtoJaHallintokorvaukset
  (process-event
    [{johto-ja-hallintokorvaukset :johto-ja-hallintokorvaukset urakan-alkuvuosi :urakan-alkuvuosi} app]
    (let [endpoint (if (<= urakan-alkuvuosi 2024)
                     :tallenna-johto-ja-hallintokorvaukset-2019
                     :tallenna-johto-ja-hallintokorvaukset-2025)
          avain (if (<= urakan-alkuvuosi 2024)
                  :johto-ja-hallintokorvaukset-2019
                  :johto-ja-hallintokorvaukset-2025)]
      (tuck-apurit/post! endpoint
        {:urakka-id (-> @tila/yleiset :urakka :id)
         :hoitovuoden-alkuvuosi (pvm/vuosi (first (:valittu-hoitokausi app)))
         avain johto-ja-hallintokorvaukset}
        {:onnistui ->TallennaJohtoJaHallintokorvauksetOnnistui
         :epaonnistui ->TallennaJohtoJaHallintokorvauksetEpaonnistui
         :paasta-virhe-lapi? true})
      (assoc app :tallennus-kesken? true)))

  TallennaJohtoJaHallintokorvauksetOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Hoidonjohtopalkkiot tallennettiin.")
    (-> app
      (assoc-in [:kustannussuunnitelma :johto-ja-hallintokorvaukset-virheet] nil)
      (assoc :tallennus-kesken? false)
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))
      (assoc :kustannussuunnitelma (:kustannussuunnitelma vastaus))))

  TallennaJohtoJaHallintokorvauksetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (let [parsitut-virheet (parsi-johto-ja-hallintokorvaus-virhe (get-in vastaus [:parse-error :original-text])
                             (get-in app [:kustannussuunnitelma :johto-ja-hallintokorvaukset]))]
      (viesti/nayta-toast!
        parsitut-virheet
        :varoitus
        viesti/viestin-nayttoaika-keskipitka)
      (-> app
        (assoc-in [:kustannussuunnitelma :johto-ja-hallintokorvaukset-virheet] parsitut-virheet)
        (assoc :tallennus-kesken? false))))

  JaaJohtoJaHallintokorvauksetTasan
  (process-event [{:keys [summa johto-ja-hallintokorvaukset-elementti]} app]
    (let [johto-ja-hallintokorvaukset (get-in app [:kustannussuunnitelma :johto-ja-hallintokorvaukset])
          kk-summa (tyokalut/round2 2 (/ summa 12))
          viimeneinen-summa (- summa (tyokalut/round2 2 (* 11 kk-summa)))
          johto-ja-hallintokorvaukset (map-indexed (fn [indeksi rivi]
                                                     (merge rivi
                                                       {:summa (if (= indeksi 11) viimeneinen-summa kk-summa)
                                                        :summa_indeksikorjattu nil}))
                                        johto-ja-hallintokorvaukset)]
      (scrollaa-muutoksiin johto-ja-hallintokorvaukset-elementti)
      (assoc-in app [:kustannussuunnitelma :johto-ja-hallintokorvaukset] johto-ja-hallintokorvaukset)))

  ValitseHoitokausiKustannussuunnitelmaan
  (process-event [{vuosi :vuosi} app]
    (let [app (-> app
                (assoc :valittu-kuukausi nil)
                ;; Lupaukset on kiinteässä linkissä kustannusten seurannan kanssa joten tarvitaan hoitokaudellekin sama avain
                (assoc :valittu-hoitokausi [(pvm/hoitokauden-alkupvm vuosi)
                                            (pvm/paivan-lopussa (pvm/hoitokauden-loppupvm (inc vuosi)))])
                (assoc :nykyhetki (pvm/nyt))
                (assoc :haku-kaynnissa? true)
                (assoc :hoitokauden-alkuvuosi vuosi))]
      ;; Haetaan kaikki välikatselmuksessa tarvittavat tiedot
      (hae-kustannussuunnitelman-tiedot (-> @tila/yleiset :urakka :id) vuosi)
      (assoc app :haku-kaynnissa? true)))

  VahvistaTaiPeruutaTavoiteJaKattohinta
  (process-event
    [{vahvista? :vahvista?} app]
    (tuck-apurit/post! :vahvista-tavoite-ja-kattohinta
      {:urakka-id (-> @tila/yleiset :urakka :id)
       :hoitovuoden-alkuvuosi (pvm/vuosi (first (:valittu-hoitokausi app)))
       :vahvista? vahvista?}
      {:onnistui ->VahvistaTaiPeruutaTavoiteJaKattohintaOnnistui
       :epaonnistui ->VahvistaTaiPeruutaTavoiteJaKattohintaEpaonnistui
       :paasta-virhe-lapi? true})
    (-> app
      (assoc :haku-kaynnissa? true)
      (assoc :tallennus-kesken? true)))

  VahvistaTaiPeruutaTavoiteJaKattohintaOnnistui
  (process-event [{:keys [vastaus]} app]
    (if (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe])
      (viesti/nayta-toast!
        "Tavoite- ja kattohinnan vahvistaminen epäonnistui!"
        :varoitus
        viesti/viestin-nayttoaika-keskipitka)
      (viesti/nayta-toast! "Tavoite- ja kattohinta vahvistettiin."))
    (scrollaa-muutoksiin "tavoite-ja-kattohinta-elementti")
    (-> app
      (assoc :tallennus-kesken? false)
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))
      (assoc :kustannussuunnitelma (:kustannussuunnitelma vastaus))))

  VahvistaTaiPeruutaTavoiteJaKattohintaEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast!
      "Tavoite- ja kattohinnan vahvistaminen epäonnistui!"
      :varoitus
      viesti/viestin-nayttoaika-keskipitka)
    (scrollaa-muutoksiin "tavoite-ja-kattohinta-elementti")
    (-> app
      (assoc :tallennus-kesken? false)))

  PoistaToimenkuva
  (process-event [{:keys [rivi]} app ]
    (let [toimenkuvat (into [] (filter #(some #{"johto-ja-hallintokorvaus"}
                                          [(:osio %)]) (:tarjous app)))
          muokatut-toimenkuvat (map (fn [m]
                                         (if (= (:nimi m) (:nimi rivi))
                                           (assoc m :poistettu true)
                                           m)) toimenkuvat)
          _ (reset! grid-toimenkuvat-atom muokatut-toimenkuvat)])
    (-> app
      (update :tarjous
        #(map (fn [m]
                (if (= (:nimi m) (:nimi rivi))
                  (assoc m :poistettu true)
                  m)) %))))

  ToggleVetolaatikonMuokkaus
  (process-event [{:keys [tila]} app]
    (-> app
      (assoc :vetolaatikon-muokkaus tila))))
