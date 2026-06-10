(ns harja.tiedot.urakka.suunnittelu.tarjous-kustannussuunnitelma-tiedot
  (:require [clojure.string :as str]
            [tuck.core :as tuck]
            [harja.tyokalut.yleiset :as tyokalut]
            [harja.pvm :as pvm]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.viesti :as viesti]
            [harja.ui.nakymasiirrin :as siirrin]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.kulut.mhu-kulut :as mhu-kulut]))

(defonce nakymassa? (atom false))

(defn laske-rivit-yhteen [rivi]
  (let [vuosikohtaiset-avaimet (filter #(str/starts-with? (name %) "vuosi-") (keys rivi))
        vuosikohtaiset-kustannukset (map #(get rivi % 0) vuosikohtaiset-avaimet)
        vuosikohtainen-summa (reduce + vuosikohtaiset-kustannukset)
        eperhoitovuosi (:eperhoitovuosi rivi 0)
        vuosien-maara (count vuosikohtaiset-avaimet)]

    ;; Jos vuosikohtaiset summat ovat nolla mutta € / hoitovuosi on annettu,
    ;; lasketaan yhteensä kertomalla € / hoitovuosi vuosien määrällä
    (if (and (= 0 vuosikohtainen-summa) (> eperhoitovuosi 0) (> vuosien-maara 0))
      (* eperhoitovuosi vuosien-maara)
      vuosikohtainen-summa)))

(defn jyvita-eperhoitovuosi-hoitovuosille
  "Jyvittää € / hoitovuosi arvon hoitovuosikohtaisille kentille"
  [rivi vahvistetut-vuodet]
  (let [eperhoitovuosi (:eperhoitovuosi rivi 0)
        vuosiavaimet (filter #(str/starts-with? (name %) "vuosi-") (keys rivi))
        ;; Vahvistetut vuodet jätetään pois, jotta niihin ei yritetä kirjoittaa
        vuosiavaimet (remove (fn [avain]
                               (let [vuosi (js/parseInt (.substring (str avain) 7))]
                                 (contains? vahvistetut-vuodet vuosi))) vuosiavaimet)]
    (if (> eperhoitovuosi 0)
      (let [jyvitetyt-arvot (zipmap vuosiavaimet (repeat eperhoitovuosi))]
        (merge rivi jyvitetyt-arvot))
      rivi)))

(defn muunna-tarjous-data
  "Muuntaa bäkkärin tarjous-datan UI-gridille sopivaan muotoon"
  [tarjous-tiedot]
  (when tarjous-tiedot
    (into [] (reduce (fn [rivit tarjous-rivi]
                       (let [vuosiarvot (reduce (fn [uusi rivi]
                                                  (-> uusi
                                                    (assoc :jarjestys (:jarjestys tarjous-rivi))
                                                    (assoc :maksukausi (:maksukausi tarjous-rivi))
                                                    (assoc :poistettu (:poistettu tarjous-rivi))
                                                    (assoc :rahavaraus-id (:rahavaraus-id tarjous-rivi))
                                                    (assoc :toimenkuva-id (:toimenkuva-id tarjous-rivi))
                                                    (assoc :tehtava-id (:tehtava-id tarjous-rivi))
                                                    (assoc :tehtavaryhma-id (:tehtavaryhma-id tarjous-rivi))
                                                    (assoc :osio (:osio tarjous-rivi))
                                                    (assoc (keyword (str "vuosi-" (:vuosi rivi))) (:summa rivi))))
                                          {} (:hoitovuosittaiset-arvot tarjous-rivi))
                             nimiarvot {:nimi (:nimi tarjous-rivi) :yhteensa (:yhteensa tarjous-rivi)}
                             lopputulos (merge vuosiarvot nimiarvot)]
                         (concat rivit [lopputulos])))
               [] tarjous-tiedot))))

(defn filtteri-hankinnat
  "Filttaa hankinnat-tiedot taulukosta"
  [taulukon-tiedot]
  (into [] (filter #(some #{"hankintakustannukset" "tavoitehintaiset-rahavaraukset"} [(:osio %)]) taulukon-tiedot)))

(defn filtteri-erillishankinnat
  "Filttaa erillishankinnat-tiedot taulukosta"
  [taulukon-tiedot]
  (into [] (filter #(some #{"erillishankinnat"} [(:osio %)]) taulukon-tiedot)))

(defn filtteri-hoidonjohtopalkkiot
  "Filttaa hoidonjohtopalkkio-tiedot taulukosta"
  [taulukon-tiedot]
  (into [] (filter #(some #{"hoidonjohtopalkkio"} [(:osio %)]) taulukon-tiedot)))

(defn filtteri-toimenkuvat
  "Filttaa johto-ja-hallintokorvaus (toimenkuvat) tiedot taulukosta"
  [taulukon-tiedot]
  (into [] (filter #(some #{"johto-ja-hallintokorvaus"} [(:osio %)]) taulukon-tiedot)))

(defn filtteri-yhteensa
  "Filtteröi yhteensa osion taulukosta"
  [taulukon-tiedot]
  (into [] (filter #(some #{"yhteensa"} [(:osio %)]) taulukon-tiedot)))

(defn laske-kaikkien-gridien-yhteensa
  "Laskee kaikkien gridien hoitovuosikohtaiset arvot yhteen"
  [hankinnat erillishankinnat hoidonjohtopalkkiot joha vuositaulukon-otsikot]
  (let [vuosiavaimet (map :nimi vuositaulukon-otsikot)
        kaikki-rivit (concat hankinnat erillishankinnat hoidonjohtopalkkiot joha)

        ;; Laske jokaisen vuoden summa kaikista grideistä
        vuosikohtaiset-summat
        (reduce (fn [summat vuosiavain]
                  (let [vuoden-summa (reduce + (map #(get % vuosiavain 0) kaikki-rivit))]
                    (assoc summat vuosiavain vuoden-summa)))
          {} vuosiavaimet)

        yhteensa (reduce + (vals vuosikohtaiset-summat))]

    {:vuosikohtaiset-summat vuosikohtaiset-summat
     :yhteensa yhteensa}))

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

(defrecord HaeTyhjatTarjouksenTiedot [])
(defrecord HaeTyhjatTarjouksenTiedotOnnistui [vastaus])
(defrecord HaeTyhjatTarjouksenTiedotEpaonnistui [vastaus])

;; Tarjous Grid-päivitys eventit
(defrecord PaivitaHankinnatGrid [hankinnat])
(defrecord PaivitaErillishankinnatGrid [erillishankinnat])
(defrecord PaivitaToimenkuvatGrid [toimenkuvat])
(defrecord PaivitaHoidonjohtopalkkioGrid [hoidonjohtopalkkiot])
(defrecord PaivitaTavoiteJaKattohintaGrid [rivit])

;; Tallennetaan tarjouksen data
(defrecord TallennaTarjouksenTiedot [])
(defrecord TallennaTarjouksenTiedotOnnistui [vastaus])
(defrecord TallennaTarjouksenTiedotEpaonnistui [vastaus])
(defrecord ToggleUusiToimenkuvaValittavana [tila])

;; Haetaan kustannussuunnitelman tiedot
(defrecord HaeKustannussuunnitelmanTiedot [])
(defrecord HaeKustannussuunnitelmanTiedotOnnistui [vastaus])
(defrecord HaeKustannussuunnitelmanTiedotEpaonnistui [vastaus])

;; Tallennetaan kilpailutettavat hankinnat kustannussuunnitelmaan
(defrecord TallennaKilpailutettavatHankinnat [kilpailutettavat-hankinnat kopioi-tuleville-vuosille?])
(defrecord PaivitaKilpailutettavatHankinnat [kilpailutettavat-hankinnat])
(defrecord TallennaKilpailutettavatHankinnatOnnistui [vastaus])
(defrecord TallennaKilpailutettavatHankinnatEpaonnistui [vastaus])

;; Tallenna erillishankinnat
(defrecord TallennaErillishankinnat [erillishankinnat kopioi-tuleville-vuosille?])
(defrecord PaivitaErillishankinnat [erillishankinnat])
(defrecord TallennaErillishankinnatOnnistui [vastaus])
(defrecord TallennaErillishankinnatEpaonnistui [vastaus])
(defrecord JaaErillishankinnatTasan [summa elementti])

;; Johto-ja-hallintokorvaus-käsittelyt
(defrecord TallennaJohtoJaHallintokorvaukset [johto-ja-hallintokorvaukset urakan-alkuvuosi kopioi-tuleville-vuosille?])
(defrecord PaivitaJohtoJaHallintokorvaukset [johto-ja-hallintokorvaukset])
(defrecord PaivitaJohtoJaHallintokorvaukset2019 [johto-ja-hallintokorvaukset toimenkuva])
(defrecord TallennaJohtoJaHallintokorvauksetOnnistui [vastaus])
(defrecord TallennaJohtoJaHallintokorvauksetEpaonnistui [vastaus])
(defrecord JaaJohtoJaHallintokorvauksetTasan [summa johto-ja-hallintokorvaukset-elementti])

;; Hoidonjohtopalkkio-käsittelyt
(defrecord TallennaHoidonjohtopalkkiot [hoidonjohtopalkkiot kopioi-tuleville-vuosille?])
(defrecord PaivitaHoidonjohtopalkkiot [hoidonjohtopalkkiot])
(defrecord TallennaHoidonjohtopalkkiotOnnistui [vastaus])
(defrecord TallennaHoidonjohtopalkkiotEpaonnistui [vastaus])
(defrecord JaaHoidonjohtopalkkiotTasan [summa hoidonjohtopalkkio-elementti])

;; Kustannusten suunnittelu -  Vahvistukset
(defrecord PaivitaHoitovuodenAlunKattohinta [kattohinta])
(defrecord VahvistaTaiPeruutaTavoiteJaKattohinta [vahvista?])
(defrecord VahvistaTaiPeruutaTavoiteJaKattohintaOnnistui [vastaus])
(defrecord VahvistaTaiPeruutaTavoiteJaKattohintaEpaonnistui [vastaus])

(defrecord ToggleVetolaatikonMuokkaus [tila])
(defrecord NollaaKustannussuunnitelmanMuutokset [])
(defrecord AsetaHankinnatMuutos [])
(defrecord AsetaErillishankinnatMuutos [])
(defrecord AsetaJJHMuutos [])
(defrecord AsetaHoidonjohtopalkkioMuutos [])

(defrecord ValitseHoitokausiKustannussuunnitelmaan [])
(defrecord PoistaToimenkuva [rivi])

(defn hae-kustannussuunnitelman-tiedot
  "Haetaan kustannussuunnitelman tiedot, jotta voidaan näyttää ne UI Gridissä.
  Vuosi on hoitovuoden alkuvuosi, jolle kustannussuunnitelma haetaan."
  [urakka-id vuosi]
  (tuck-apurit/post! :hae-kustannussuunnitelman-tiedot
    {:urakka-id urakka-id
     :hoitovuoden-alkuvuosi vuosi}
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

(defn kasittele-tarjouksen-vastaus [vastaus app]
  (let [tarjous-tiedot (:tarjous vastaus)
        taulukon-tiedot (muunna-tarjous-data tarjous-tiedot)]
    (-> app
      (assoc :haku-kaynnissa? false)
      (assoc :tallennus-kesken? false)
      (assoc :tarjous (:tarjous vastaus))
      (assoc :kaikki-toimenkuvat (:kaikki-toimenkuvat vastaus))
      (assoc :muokkaa-kattohinta-kasin (:muokkaa-kattohinta-kasin vastaus))
      (assoc :viimeisin-muokkaus (:viimeisin-muokkaus vastaus))
      (assoc :viimeisin-muokkaaja (:viimeisin-muokkaaja vastaus))
      (assoc :hankinnat (filtteri-hankinnat taulukon-tiedot))
      (assoc :kattohintakerroin (:kattohintakerroin vastaus))
      (assoc :erillishankinnat (filtteri-erillishankinnat taulukon-tiedot))
      (assoc :hoidonjohtopalkkiot (filtteri-hoidonjohtopalkkiot taulukon-tiedot))
      (assoc :toimenkuvat (filtteri-toimenkuvat taulukon-tiedot))
      (assoc :yhteensa (filtteri-yhteensa taulukon-tiedot))
      (assoc :vahvistetut-vuodet (:vahvistetut-vuodet vastaus))
      (assoc :urakka-id (:urakka-id vastaus)))))

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
    (kasittele-tarjouksen-vastaus vastaus app))

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
    (-> (kasittele-tarjouksen-vastaus vastaus app)
      (assoc :tallentamattomia-muutoksia? false)))

  HaeTyhjatTarjouksenTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  TallennaTarjouksenTiedot
  (process-event
    [_ app]
    (let [kaikki-hankinnat (concat (:hankinnat app) (:erillishankinnat app) (:hoidonjohtopalkkiot app))
          ;; Muutetaan formilta saatu tarjous oikeaan muotoon
          muunnetut-tarjousrivit (map #(muunna-vuodet %) kaikki-hankinnat)
          muunnetut-toimenkuvarivit (map #(muunna-vuodet %) (:toimenkuvat app))
          muunnetut-yhteensa-rivit (map #(muunna-vuodet %) (:yhteensa app))
          tarjous (concat muunnetut-tarjousrivit muunnetut-toimenkuvarivit muunnetut-yhteensa-rivit)
          muunnettu-tarjous {:tarjous tarjous}
          muunnettu-tarjous (assoc muunnettu-tarjous :urakka-id (-> @tila/yleiset :urakka :id))]
      (tuck-apurit/post! :tallenna-tarjouksen-tiedot
        muunnettu-tarjous
        {:onnistui ->TallennaTarjouksenTiedotOnnistui
         :epaonnistui ->TallennaTarjouksenTiedotEpaonnistui
         :paasta-virhe-lapi? true})
      (assoc app :tallennus-kesken? true)))

  TallennaTarjouksenTiedotOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Tarjous tallennettiin onnistuneesti.")
    (-> (kasittele-tarjouksen-vastaus vastaus app)
      (assoc :tallentamattomia-muutoksia? false)
      (assoc :uusi-toimenkuva-valittavana false)))

  TallennaTarjouksenTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (:virhe (:response vastaus)) :varoitus viesti/viestin-nayttoaika-pitka)
    (assoc app :tallennus-kesken? false))

  ToggleUusiToimenkuvaValittavana
  (process-event [{tila :tila} app]
    (assoc app :uusi-toimenkuva-valittavana tila))

  HaeKustannussuunnitelmanTiedot
  (process-event
    [_ app]
    (hae-kustannussuunnitelman-tiedot (-> @tila/yleiset :urakka :id) (pvm/vuosi (first @u/valittu-hoitokausi)))
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
      (assoc :tulevaisuudessa-arvoja? (:tulevaisuudessa-arvoja? vastaus))
      (assoc :viimeinen-hoitovuosi? (:viimeinen-hoitovuosi? vastaus))
      (assoc :kustannussuunnitelma (:kustannussuunnitelma vastaus))
      (assoc :vanha-urakka? (:vanha-urakka? vastaus))
      (assoc :tallentamattomia-muutoksia? false)))

  HaeKustannussuunnitelmanTiedotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! (str "Tietojen haku epäonnistui: " (pr-str vastaus)) :varoitus viesti/viestin-nayttoaika-pitka)
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
                      :kaikki-alkukausi (apply + (map :alkukausi muuttuneet))
                      :kaikki-alkukausi-indeksikorjattu (apply + (map :alkukausi-indeksikorjattu muuttuneet))
                      :kaikki-loppukausi (apply + (map :loppukausi muuttuneet))
                      :kaikki-loppukausi-indeksikorjattu (apply + (map :loppukausi-indeksikorjattu muuttuneet))
                      :kaikki-yhteensa (+ (apply + (map :alkukausi muuttuneet)) (apply + (map :loppukausi muuttuneet)))
                      :kaikki-yhteensa-indeksikorjattu (+ (apply + (map :alkukausi-indeksikorjattu muuttuneet)) (apply + (map :loppukausi-indeksikorjattu muuttuneet)))
                      :viimeisin-muokkaus (:viimeisin-muokkaus (last (get-in app [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet])))
                      :viimeisin-muokkaaja (:viimeisin-muokkaaja (last (get-in app [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet])))}
          muuttuneet (conj muuttuneet yhteenveto)]
      (-> app
        (assoc-in [:kustannussuunnitelma :kilpailutettavat-hankinnat-virheet] nil)
        (assoc-in [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet] muuttuneet))))

  TallennaKilpailutettavatHankinnat
  (process-event
    [{kilpailutettavat-hankinnat :kilpailutettavat-hankinnat
      kopioi-tuleville-vuosille? :kopioi-tuleville-vuosille?} app]
    (let [vuosi (pvm/vuosi (first @u/valittu-hoitokausi))]
      (tuck-apurit/post! :tallenna-kilpailutettavat-hankinnat
        {:urakka-id (-> @tila/yleiset :urakka :id)
         :hoitovuoden-alkuvuosi vuosi
         :toimenpiteet kilpailutettavat-hankinnat
         :kopioi-tuleville-vuosille? kopioi-tuleville-vuosille?}
        {:onnistui ->TallennaKilpailutettavatHankinnatOnnistui
         :epaonnistui ->TallennaKilpailutettavatHankinnatEpaonnistui
         :paasta-virhe-lapi? true})
      (-> app
        (assoc :tallennus-kesken? true))))

  TallennaKilpailutettavatHankinnatOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Kilpailutettavat hankinnat tallennettiin.")
    (-> app
      (assoc-in [:kustannussuunnitelma :kilpailutettavat-hankinnat-virheet] nil)
      (assoc :tallennus-kesken? false)
      (assoc :onko-jjh-muutoksia? false)
      (assoc :onko-hankinnat-muutoksia? false)
      (assoc :onko-erillishankinnat-muutoksia? false)
      (assoc :onko-hoidonjohtopalkkio-muutoksia? false)
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))
      (assoc :tulevaisuudessa-arvoja? (:tulevaisuudessa-arvoja? vastaus))
      (assoc :kustannussuunnitelma (:kustannussuunnitelma vastaus))
      (assoc :vanha-urakka? (:vanha-urakka? vastaus))
      (assoc :tallentamattomia-muutoksia? false)))

  TallennaKilpailutettavatHankinnatEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (let [parsitut-virheet (parsi-kilpailutettavat-hankinnat-virhe (get-in vastaus [:parse-error :original-text])
                             (get-in app [:kustannussuunnitelma :kilpailutettavat-hankinnat :toimenpiteet]))]
      (viesti/nayta-toast!
        parsitut-virheet
        :varoitus
        viesti/viestin-nayttoaika-pitka)
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
    [{erillishankinnat :erillishankinnat kopioi-tuleville-vuosille? :kopioi-tuleville-vuosille?} app]
    (tuck-apurit/post! :tallenna-erillishankinnat
      {:urakka-id (-> @tila/yleiset :urakka :id)
       :hoitovuoden-alkuvuosi (pvm/vuosi (first @u/valittu-hoitokausi))
       :erillishankinnat erillishankinnat
       :kopioi-tuleville-vuosille? kopioi-tuleville-vuosille?}
      {:onnistui ->TallennaErillishankinnatOnnistui
       :epaonnistui ->TallennaErillishankinnatEpaonnistui
       :paasta-virhe-lapi? true})
    (-> app
      (assoc :tallennus-kesken? true)
      (assoc :onko-erillishankinnat-muutoksia? false)))

  TallennaErillishankinnatOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Erillishankinnat tallennettiin onnistuneesti.")
    (-> app
      (assoc-in [:kustannussuunnitelma :erillishankinnat-virheet] nil)
      (assoc :tallennus-kesken? false)
      (assoc :onko-erillishankinnat-muutoksia? false)
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))
      (assoc :tulevaisuudessa-arvoja? (:tulevaisuudessa-arvoja? vastaus))
      (assoc :kustannussuunnitelma (:kustannussuunnitelma vastaus))
      (assoc :vanha-urakka? (:vanha-urakka? vastaus))
      (assoc :tallentamattomia-muutoksia? false)))

  TallennaErillishankinnatEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (let [parsitut-virheet (parsi-erillishankinnat-virhe (get-in vastaus [:parse-error :original-text])
                             (get-in app [:kustannussuunnitelma :erillishankinnat]))]
      (viesti/nayta-toast!
        parsitut-virheet
        :varoitus
        viesti/viestin-nayttoaika-pitka)
      (-> app
        (assoc-in [:kustannussuunnitelma :erillishankinnat-virheet] parsitut-virheet)
        (assoc :tallennus-kesken? false))))

  JaaErillishankinnatTasan
  (process-event [{:keys [summa elementti]} app]
    (let [indeksikerroin (get-in app [:kustannussuunnitelma :indeksikerroin])
          erillishankinnat (get-in app [:kustannussuunnitelma :erillishankinnat])
          kk-summa (tyokalut/round2 2 (/ summa 12))
          viimeneinen-summa (- summa (tyokalut/round2 2 (* 11 kk-summa)))
          erillishankinnat (map-indexed (fn [indeksi rivi]
                                          (let [summa (if (= indeksi 11) viimeneinen-summa kk-summa)
                                                summa-indeksikorjattu (when indeksikerroin
                                                                        (tyokalut/round2 2 (* summa indeksikerroin)))]
                                            (merge rivi
                                              {:summa summa
                                               :summa_indeksikorjattu summa-indeksikorjattu})))
                             erillishankinnat)]
      (siirrin/siirry-elementin-id elementti 5)
      (-> app
        (assoc :onko-erillishankinnat-muutoksia? true :tallentamattomia-muutoksia? true)
        (assoc-in [:kustannussuunnitelma :erillishankinnat] erillishankinnat))))

  PaivitaHoidonjohtopalkkiot
  (process-event
    [{hoidonjohtopalkkiot :hoidonjohtopalkkiot} app]
    (let [muuttuneet (sort-by (juxt :vuosi :kuukausi) (vec hoidonjohtopalkkiot))]
      (-> app
        (assoc-in [:kustannussuunnitelma :hoidonjohtopalkkiot-virheet] nil)
        (assoc-in [:kustannussuunnitelma :hoidonjohtopalkkiot] muuttuneet))))

  TallennaHoidonjohtopalkkiot
  (process-event
    [{hoidonjohtopalkkiot :hoidonjohtopalkkiot kopioi-tuleville-vuosille? :kopioi-tuleville-vuosille?} app]
    (tuck-apurit/post! :tallenna-hoidonjohtopalkkiot
      {:urakka-id (-> @tila/yleiset :urakka :id)
       :hoitovuoden-alkuvuosi (pvm/vuosi (first @u/valittu-hoitokausi))
       :hoidonjohtopalkkiot hoidonjohtopalkkiot
       :kopioi-tuleville-vuosille? kopioi-tuleville-vuosille?}
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
      (assoc :onko-jjh-muutoksia? false)
      (assoc :onko-hankinnat-muutoksia? false)
      (assoc :onko-erillishankinnat-muutoksia? false)
      (assoc :onko-hoidonjohtopalkkio-muutoksia? false)
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))
      (assoc :tulevaisuudessa-arvoja? (:tulevaisuudessa-arvoja? vastaus))
      (assoc :kustannussuunnitelma (:kustannussuunnitelma vastaus))
      (assoc :vanha-urakka? (:vanha-urakka? vastaus))
      (assoc :tallentamattomia-muutoksia? false)))

  TallennaHoidonjohtopalkkiotEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (let [parsitut-virheet (parsi-hoidonjohtopalkkiot-virhe (get-in vastaus [:parse-error :original-text])
                             (get-in app [:kustannussuunnitelma :hoidonjohtopalkkiot]))]
      (viesti/nayta-toast!
        parsitut-virheet
        :varoitus
        viesti/viestin-nayttoaika-pitka)
      (-> app
        (assoc-in [:kustannussuunnitelma :hoidonjohtopalkkiot-virheet] parsitut-virheet)
        (assoc :tallennus-kesken? false))))

  JaaHoidonjohtopalkkiotTasan
  (process-event [{:keys [summa hoidonjohtopalkkio-elementti]} app]
    (let [indeksikerroin (get-in app [:kustannussuunnitelma :indeksikerroin])
          hoidonjohtopalkkiot (get-in app [:kustannussuunnitelma :hoidonjohtopalkkiot])
          kk-summa (tyokalut/round2 2 (/ summa 12))
          viimeneinen-summa (- summa (tyokalut/round2 2 (* 11 kk-summa)))
          hoidonjohtopalkkiot (map-indexed (fn [indeksi rivi]
                                             (let [summa (if (= indeksi 11) viimeneinen-summa kk-summa)
                                                   summa-indeksikorjattu (when indeksikerroin
                                                                           (tyokalut/round2 2 (* summa indeksikerroin)))]
                                               (merge rivi
                                                 {:summa summa
                                                  :summa_indeksikorjattu summa-indeksikorjattu})))
                                hoidonjohtopalkkiot)]
      (siirrin/siirry-elementin-id hoidonjohtopalkkio-elementti 5)
      (-> app
        (assoc :onko-hoidonjohtopalkkio-muutoksia? true :tallentamattomia-muutoksia? true)
        (assoc-in [:kustannussuunnitelma :hoidonjohtopalkkiot] hoidonjohtopalkkiot))))

  PaivitaJohtoJaHallintokorvaukset
  (process-event
    [{johto-ja-hallintokorvaukset :johto-ja-hallintokorvaukset} app]
    (let [muuttuneet (sort-by (juxt :vuosi :kuukausi) (vec johto-ja-hallintokorvaukset))]
      (-> app
        (assoc-in [:kustannussuunnitelma :johto-ja-hallintokorvaukset-virheet] nil)
        (assoc-in [:kustannussuunnitelma :johto-ja-hallintokorvaukset] muuttuneet)
        (assoc :tallentamattomia-muutoksia? true))))

  ;; Vanhat toimenkuvat vaativat toimenkuvan kokonaissumman uudelleen laskennan
  PaivitaJohtoJaHallintokorvaukset2019
  (process-event
    [{johto-ja-hallintokorvaukset :johto-ja-hallintokorvaukset toimenkuva :toimenkuva} app]
    (let [muuttuneet (sort-by (juxt :vuosi :kuukausi) (vec johto-ja-hallintokorvaukset))]
      (-> app
        (assoc-in [:kustannussuunnitelma :johto-ja-hallintokorvaukset-virheet] nil)
        (assoc-in [:kustannussuunnitelma :johto-ja-hallintokorvaukset] muuttuneet)
        (assoc :tallentamattomia-muutoksia? true))))

  TallennaJohtoJaHallintokorvaukset
  (process-event
    [{:keys [johto-ja-hallintokorvaukset urakan-alkuvuosi kopioi-tuleville-vuosille?]} app]
    (let [endpoint (if (<= urakan-alkuvuosi 2024)
                     :tallenna-johto-ja-hallintokorvaukset-2019
                     :tallenna-johto-ja-hallintokorvaukset-2025)
          avain (if (<= urakan-alkuvuosi 2024)
                  :johto-ja-hallintokorvaukset-2019
                  :johto-ja-hallintokorvaukset-2025)]
      (tuck-apurit/post! endpoint
        {:urakka-id (-> @tila/yleiset :urakka :id)
         :hoitovuoden-alkuvuosi (pvm/vuosi (first @u/valittu-hoitokausi))
         avain johto-ja-hallintokorvaukset
         :kopioi-tuleville-vuosille? kopioi-tuleville-vuosille?}
        {:onnistui ->TallennaJohtoJaHallintokorvauksetOnnistui
         :epaonnistui ->TallennaJohtoJaHallintokorvauksetEpaonnistui
         :paasta-virhe-lapi? true})
      (assoc app :tallennus-kesken? true)))

  TallennaJohtoJaHallintokorvauksetOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Johto- ja Hallintokorvaukset tallennettiin.")
    (-> app
      (assoc-in [:kustannussuunnitelma :johto-ja-hallintokorvaukset-virheet] nil)
      (assoc :tallennus-kesken? false)
      (assoc :onko-jjh-muutoksia? false)
      (assoc :onko-hankinnat-muutoksia? false)
      (assoc :onko-erillishankinnat-muutoksia? false)
      (assoc :onko-hoidonjohtopalkkio-muutoksia? false)
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))
      (assoc :tulevaisuudessa-arvoja? (:tulevaisuudessa-arvoja? vastaus))
      (assoc :kustannussuunnitelma (:kustannussuunnitelma vastaus))
      (assoc :vanha-urakka? (:vanha-urakka? vastaus))
      (assoc :tallentamattomia-muutoksia? false)))

  TallennaJohtoJaHallintokorvauksetEpaonnistui
  (process-event [{:keys [vastaus]} app]
    (let [parsitut-virheet (parsi-johto-ja-hallintokorvaus-virhe (get-in vastaus [:parse-error :original-text])
                             (get-in app [:kustannussuunnitelma :johto-ja-hallintokorvaukset]))]
      (viesti/nayta-toast!
        parsitut-virheet
        :varoitus
        viesti/viestin-nayttoaika-pitka)
      (-> app
        (assoc-in [:kustannussuunnitelma :johto-ja-hallintokorvaukset-virheet] parsitut-virheet)
        (assoc :tallennus-kesken? false))))

  JaaJohtoJaHallintokorvauksetTasan
  (process-event [{:keys [summa johto-ja-hallintokorvaukset-elementti]} app]
    (let [indeksikerroin (get-in app [:kustannussuunnitelma :indeksikerroin])
          johto-ja-hallintokorvaukset (get-in app [:kustannussuunnitelma :johto-ja-hallintokorvaukset])
          kk-summa (tyokalut/round2 2 (/ summa 12))
          viimeneinen-summa (- summa (tyokalut/round2 2 (* 11 kk-summa)))
          johto-ja-hallintokorvaukset (map-indexed (fn [indeksi rivi]
                                                     (let [summa (if (= indeksi 11) viimeneinen-summa kk-summa)
                                                           summa-indeksikorjattu (when indeksikerroin
                                                                                   (tyokalut/round2 2 (* summa indeksikerroin)))]
                                                       (merge rivi
                                                         {:summa summa
                                                          :summa_indeksikorjattu summa-indeksikorjattu})))
                                        johto-ja-hallintokorvaukset)]
      (siirrin/siirry-elementin-id johto-ja-hallintokorvaukset-elementti 5)
      (-> app
        (assoc :onko-jjh-muutoksia? true :tallentamattomia-muutoksia? true)
        (assoc-in [:kustannussuunnitelma :johto-ja-hallintokorvaukset] johto-ja-hallintokorvaukset))))

  ValitseHoitokausiKustannussuunnitelmaan
  (process-event [_ app]
    (let [vuosi (pvm/vuosi (first @u/valittu-hoitokausi))
          _ (js/console.log "ValitseHoitokausiKustannussuunnitelmaan vuosi:" (pr-str vuosi))
          app (-> app
                (assoc :valittu-kuukausi nil)
                ;; Lupaukset on kiinteässä linkissä kustannusten seurannan kanssa joten tarvitaan hoitokaudellekin sama avain
                (assoc :valittu-hoitokausi @u/valittu-hoitokausi)
                (assoc :nykyhetki (pvm/nyt))
                (assoc :haku-kaynnissa? true)
                (assoc :hoitokauden-alkuvuosi vuosi))]
      ;; Haetaan kaikki välikatselmuksessa tarvittavat tiedot
      (hae-kustannussuunnitelman-tiedot (-> @tila/yleiset :urakka :id) vuosi)
      (-> app
        (assoc :onko-hankinnat-muutoksia? false)
        (assoc :onko-jjh-muutoksia? false)
        (assoc :onko-hoidonjohtopalkkio-muutoksia? false)
        (assoc :onko-erillishankinnat-muutoksia? false)
        (assoc :haku-kaynnissa? true))))

  PaivitaHoitovuodenAlunKattohinta
  (process-event
    [{kattohinta :kattohinta} app]
    (let [kattohinta (if-not (str/blank? kattohinta)
                       (js/parseInt kattohinta)
                       0)]
      (-> app
        (assoc :kattohinta-virhe false)
        (assoc :paivitetty-hoitovuoden-alun-kattohinta kattohinta))))

  VahvistaTaiPeruutaTavoiteJaKattohinta
  (process-event
    [{vahvista? :vahvista?} app]
    (tuck-apurit/post! :vahvista-tavoite-ja-kattohinta
      {:urakka-id (-> @tila/yleiset :urakka :id)
       :hoitovuoden-alkuvuosi (pvm/vuosi (first @u/valittu-hoitokausi))
       :vahvista? vahvista?
       :paivitetty-kattohinta (:paivitetty-hoitovuoden-alun-kattohinta app)}
      {:onnistui ->VahvistaTaiPeruutaTavoiteJaKattohintaOnnistui
       :epaonnistui ->VahvistaTaiPeruutaTavoiteJaKattohintaEpaonnistui
       :paasta-virhe-lapi? true})
    (-> app
      (assoc :onko-hankinnat-muutoksia? false)
      (assoc :onko-jjh-muutoksia? false)
      (assoc :onko-hoidonjohtopalkkio-muutoksia? false)
      (assoc :onko-erillishankinnat-muutoksia? false)
      (assoc :haku-kaynnissa? true)
      (assoc :tallennus-kesken? true)))

  VahvistaTaiPeruutaTavoiteJaKattohintaOnnistui
  (process-event [{:keys [vastaus]} app]
    (if (get-in vastaus [:kustannussuunnitelma :vahvistus-virhe])
      (viesti/nayta-toast!
        "Tavoite- ja kattohinnan vahvistaminen epäonnistui!"
        :varoitus
        viesti/viestin-nayttoaika-pitka)
      (viesti/nayta-toast! "Tavoite- ja kattohinta vahvistettiin."))
    (siirrin/siirry-elementin-id "tavoite-ja-kattohinta-elementti" 5)
    (-> app
      (assoc :tallennus-kesken? false)
      (assoc :haku-kaynnissa? false)
      (assoc :tarjous (:tarjous vastaus))
      (assoc :kustannussuunnitelma (:kustannussuunnitelma vastaus))
      (assoc :vanha-urakka? (:vanha-urakka? vastaus))))

  VahvistaTaiPeruutaTavoiteJaKattohintaEpaonnistui
  (process-event [{vastaus :vastaus} app]
    (let [viesti (if (get-in vastaus [:response :virhe])
                   (get-in vastaus [:response :virhe])
                   "Tavoite- ja kattohinnan vahvistaminen epäonnistui!")
          kattohinta-virhe? (str/includes? viesti "Annettu kattohinta")]
      (viesti/nayta-toast! viesti :varoitus viesti/viestin-nayttoaika-pitka)
      (siirrin/siirry-elementin-id "tavoite-ja-kattohinta-elementti" 5)
      (-> app
        (assoc :kattohinta-virhe (or kattohinta-virhe? false))
        (assoc :tallennus-kesken? false)
        (assoc :haku-kaynnissa? false))))

  PoistaToimenkuva
  (process-event [{:keys [rivi]} app]
    (let [toimenkuvat (:toimenkuvat app)
          muokatut-toimenkuvat (map (fn [m]
                                      (if (= (:nimi m) (:nimi rivi))
                                        (assoc m :poistettu true)
                                        m)) toimenkuvat)]
      (-> app
        (assoc :toimenkuvat muokatut-toimenkuvat)
        (update :tarjous
          #(map (fn [m]
                  (if (= (:nimi m) (:nimi rivi))
                    (assoc m :poistettu true)
                    m)) %)))))

  PaivitaHankinnatGrid
  (process-event [{:keys [hankinnat]} app]
    (-> app
      (assoc :hankinnat (sort-by :jarjestys hankinnat))
      (assoc :tallentamattomia-muutoksia? true)))

  PaivitaErillishankinnatGrid
  (process-event [{:keys [erillishankinnat]} app]
    (-> app
      (assoc :erillishankinnat erillishankinnat)
      (assoc :tallentamattomia-muutoksia? true)))

  PaivitaToimenkuvatGrid
  (process-event [{:keys [toimenkuvat]} app]
    (-> app
      (assoc :toimenkuvat toimenkuvat)
      (assoc :tallentamattomia-muutoksia? true)))

  PaivitaHoidonjohtopalkkioGrid
  (process-event [{:keys [hoidonjohtopalkkiot]} app]
    (-> app
      (assoc :hoidonjohtopalkkiot hoidonjohtopalkkiot)
      (assoc :tallentamattomia-muutoksia? true)))

  PaivitaTavoiteJaKattohintaGrid
  (process-event [{rivit :rivit} app]
    (-> app
      (assoc :yhteensa (map #(dissoc % :fmt :koskematon :muokatava? :eperhoitovuosi) rivit))
      (assoc :tallentamattomia-muutoksia? true)))

  ToggleVetolaatikonMuokkaus
  (process-event [{:keys [tila]} app]
    (-> app
      (assoc :vetolaatikon-muokkaus tila)
      (assoc :tallentamattomia-muutoksia? true)))

  NollaaKustannussuunnitelmanMuutokset
  (process-event [_ app]
    (-> app
      (assoc :onko-hankinnat-muutoksia? false)
      (assoc :onko-jjh-muutoksia? false)
      (assoc :onko-hoidonjohtopalkkio-muutoksia? false)
      (assoc :onko-erillishankinnat-muutoksia? false)
      (assoc :tallentamattomia-muutoksia? false)))

  AsetaHankinnatMuutos
  (process-event [_ app]
    (-> app
      (assoc :onko-hankinnat-muutoksia? true)
      (assoc :tallentamattomia-muutoksia? true)))

  AsetaErillishankinnatMuutos
  (process-event [_ app]
    (-> app
      (assoc :onko-erillishankinnat-muutoksia? true)
      (assoc :tallentamattomia-muutoksia? true)))

  AsetaJJHMuutos
  (process-event [_ app]
    (-> app
      (assoc :onko-jjh-muutoksia? true)))

  AsetaHoidonjohtopalkkioMuutos
  (process-event [_ app]
    (-> app
      (assoc :onko-hoidonjohtopalkkio-muutoksia? true)
      (assoc :tallentamattomia-muutoksia? true))))
