(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.loki :refer [log error]]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.urakka :as ur]
            [harja.domain.vesivaylat.vayla :as va]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [cljs.core.async :refer [<!]]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [harja.ui.viesti :as viesti]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tyokalut.tuck :as tuck-tyokalut]
            [harja.ui.kartta.esitettavat-asiat :as kartta]
            [clojure.set :as set]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(defrecord ValitseToimenpide [tiedot toimenpiteet])
(defrecord ValitseToimenpiteet [tila toimenpiteet])
(defrecord ValitseTyolaji [tiedot toimenpiteet])
(defrecord ValitseVayla [tiedot toimenpiteet])
(defrecord AsetaInfolaatikonTila [tunniste uusi-tila lisa-funktiot])
(defrecord ToimenpiteetSiirretty [toimenpiteet])
(defrecord ToimenpiteetEiSiirretty [])
(defrecord LisaaToimenpiteelleLiite [tiedot])
(defrecord LiiteLisatty [vastaus tiedot])
(defrecord LiiteEiLisatty [])
(defrecord PoistaToimenpiteenLiite [tiedot])
(defrecord LiitePoistettu [vastaus tiedot])
(defrecord LiiteEiPoistettu [])
(defrecord HaeToimenpiteidenTurvalaitteetKartalle [toimenpiteet])
(defrecord TurvalaitteetKartalleHaettu [tulos haetut])
(defrecord TurvalaitteetKartalleEiHaettu [virhe haetut])
(defrecord KorostaToimenpideKartalla [toimenpide lisa-funktiot])

(def valintojen-avaimet [:urakka-id :sopimus-id :aikavali
                         :vaylatyyppi :vaylanro :turvalaitenro
                         :tyolaji :tyoluokka :toimenpide
                         :vain-vikailmoitukset?])

(defn arvot-pudotusvalikko-valinnoiksi [kartta]
  (into [nil] (distinct (vals kartta))))

(defn toimenpiteet-tyolajilla [toimenpiteet tyolaji]
  (filterv #(= (::to/tyolaji %) tyolaji) toimenpiteet))

(defn kaikki-valittu? [toimenpiteet]
  (every? true? (map :valittu? toimenpiteet)))

(defn joku-valittu? [toimenpiteet]
  (boolean (some :valittu? toimenpiteet)))

(defn mitaan-ei-valittu? [tyolajin-toimenpiteet]
  (every? (comp not true?)
          (map :valittu? tyolajin-toimenpiteet)))

(defn valinnan-tila [tyolajin-toimenpiteet]
  (cond (kaikki-valittu? tyolajin-toimenpiteet) true
        (mitaan-ei-valittu? tyolajin-toimenpiteet) false
        :default :harja.ui.kentat/indeterminate))

(defn valitut-toimenpiteet [toimenpiteet]
  (filter :valittu? toimenpiteet))

(defn poista-toimenpiteet [toimenpiteet poistettavat-toimenpide-idt]
  (filter #(not (poistettavat-toimenpide-idt (::to/id %))) toimenpiteet))

(defn toimenpiteiden-toiminto-suoritettu [toimenpiteiden-lkm toiminto]
  (str toimenpiteiden-lkm " "
       (if (= 1 toimenpiteiden-lkm) "toimenpide" "toimenpidettä")
       " " toiminto "."))

(defn toimenpiteiden-hakukyselyn-argumentit [{:keys [urakka-id sopimus-id aikavali
                                                     vaylatyyppi vaylanro turvalaitenro
                                                     tyolaji tyoluokka toimenpide
                                                     vain-vikailmoitukset?] :as valinnat}]
  (spec-apurit/poista-nil-avaimet {::to/urakka-id urakka-id
                                   ::to/sopimus-id sopimus-id
                                   ::va/vaylatyyppi vaylatyyppi
                                   ::to/vaylanro vaylanro
                                   ::to/turvalaitenro turvalaitenro
                                   ::to/reimari-tyolaji (when tyolaji (to/reimari-tyolaji-avain->koodi tyolaji))
                                   ::to/reimari-tyoluokat (when tyoluokka (to/reimari-tyoluokka-avain->koodi tyoluokka))
                                   ::to/reimari-toimenpidetyypit (when toimenpide (to/reimari-toimenpidetyyppi-avain->koodi toimenpide))
                                   :alku (first aikavali)
                                   :loppu (second aikavali)
                                   :vikailmoitukset? vain-vikailmoitukset?}))

(defn yhdista-tilat! [mun-tila sen-tila]
  (swap! mun-tila update :valinnat #(merge % (:valinnat @sen-tila)))
  mun-tila)

(defn toimenpiteet-aikajarjestyksessa [toimenpiteet]
  (sort-by
    ::to/pvm
    pvm/jalkeen?
    toimenpiteet))

(defn korosta-turvalaite-kartalla? [app]
  ;; Tämä funktio oli alunperin kartalla-esitettavaan-muotoon funktion
  ;; haluama valittu?-fn, siksi palautetaan funktio. Pidetään nykyinen
  ;; malli, niin kartan toiminnallisuutta on helpompi muuttaa jälkikäteen.
  (fn [turvalaite]
    (boolean
      (when-let [setti (:korostetut-turvalaitteet app)]
       (setti (::tu/turvalaitenro turvalaite))))))

(defn turvalaitteen-toimenpiteet [turvalaite app]
  (filterv #(= (::tu/turvalaitenro turvalaite)
              (get-in % [::to/turvalaite ::tu/turvalaitenro]))
          (:toimenpiteet app)))

(defn kartalla-naytettavat-turvalaitteet [turvalaitteet app]
  (if (empty? (:korostetut-turvalaitteet app))
    turvalaitteet

    (filter (korosta-turvalaite-kartalla? app) turvalaitteet)))

(defn turvalaitteet-kartalle [turvalaitteet app]
  (kartta/kartalla-esitettavaan-muotoon
    (kartalla-naytettavat-turvalaitteet turvalaitteet app)
    (constantly false)
    (comp
      (map #(assoc % :tyyppi-kartalla :turvalaite))
      (map #(set/rename-keys % {::tu/koordinaatit :sijainti}))
      (map #(assoc % :toimenpiteet (turvalaitteen-toimenpiteet % app))))))

(defn paivita-kartta [app]
  (assoc app :turvalaitteet-kartalla (turvalaitteet-kartalle (:turvalaitteet app) app)))

(defn korosta-kartalla [turvalaitenumero-set app]
  (-> (assoc app :korostetut-turvalaitteet turvalaitenumero-set)
      (paivita-kartta)))

(defn siirra-valitut! [palvelu app]
  (tuck-tyokalut/post! palvelu
                       {::to/urakka-id (get-in app [:valinnat :urakka-id])
                               ::to/idt (set (map ::to/id (valitut-toimenpiteet (:toimenpiteet app))))}
                       {:onnistui ->ToimenpiteetSiirretty
                               :epaonnistui ->ToimenpiteetEiSiirretty})
  (assoc app :siirto-kaynnissa? true))

(defn korosta-harjassa-luotu [toimenpide]
  (if (::to/harjassa-luotu toimenpide)
    (assoc toimenpide :lihavoi true)
    toimenpide))

(defn korosta-harjassa-luodut [vastaus]
  (map korosta-harjassa-luotu vastaus))

(extend-protocol tuck/Event
  ValitseToimenpide
  (process-event [{tiedot :tiedot listan-toimenpiteet :toimenpiteet} {:keys [toimenpiteet] :as app}]
    (let [toimenpide-id (:id tiedot)
          valinta (:valinta tiedot)
          paivitetty-toimenpide (-> (to/toimenpide-idlla listan-toimenpiteet toimenpide-id)
                                    (assoc :valittu? valinta))]
      (assoc app :toimenpiteet (map #(if (= (::to/id %) toimenpide-id) paivitetty-toimenpide %)
                                     toimenpiteet))))

  ValitseToimenpiteet
  (process-event [{tila :tila valittavat :toimenpiteet} {:keys [toimenpiteet] :as app}]
    (let [idt (into #{} (map ::to/id valittavat))]
      (assoc app :toimenpiteet (map #(if (idt (::to/id %))
                                       (assoc % :valittu? tila)
                                       %)
                                    toimenpiteet))))

  ValitseTyolaji
  (process-event [{tiedot :tiedot listan-toimenpiteet :toimenpiteet} {:keys [toimenpiteet] :as app}]
    (let [tyolaji (:tyolaji tiedot)
          valinta (:valinta tiedot)
          paivitetyt-toimenpiteet (mapv #(if (= (::to/tyolaji %) tyolaji)
                                           (assoc % :valittu? valinta)
                                           %)
                                        listan-toimenpiteet)
          paivitetyt-idt (into #{} (map ::to/id paivitetyt-toimenpiteet))
          paivittamattomat (remove (comp paivitetyt-idt ::to/id) toimenpiteet)]
      (assoc app :toimenpiteet (concat paivitetyt-toimenpiteet paivittamattomat))))

  ValitseVayla
  (process-event [{tiedot :tiedot listan-toimenpiteet :toimenpiteet} {:keys [toimenpiteet] :as app}]
    (let [vaylanro (:vaylanro tiedot)
          valinta (:valinta tiedot)
          paivitetyt-toimenpiteet (mapv #(if (= (get-in % [::to/vayla ::va/vaylanro]) vaylanro)
                                           (assoc % :valittu? valinta)
                                           %)
                                        listan-toimenpiteet)
          paivitetyt-idt (into #{} (map ::to/id paivitetyt-toimenpiteet))
          paivittamattomat (remove (comp paivitetyt-idt ::to/id) toimenpiteet)]
      (assoc app :toimenpiteet (concat paivitetyt-toimenpiteet paivittamattomat))))

  AsetaInfolaatikonTila
  (process-event [{tunniste :tunniste
                   uusi-tila :uusi-tila
                   fn :lisa-funktiot} app]
    (let [fn (or fn [])]
      ((apply comp fn)
        (if tunniste
          (cond->> (assoc app :infolaatikko-nakyvissa (merge (:infolaatikko-nakyvissa app)
                                                             {tunniste uusi-tila}))
                   (false? uusi-tila)
                   (korosta-kartalla nil))
          app))))

  ToimenpiteetSiirretty
  (process-event [{toimenpiteet :toimenpiteet} app]
    (viesti/nayta! (toimenpiteiden-toiminto-suoritettu (count toimenpiteet) "siirretty") :success)
    (assoc app :toimenpiteet (poista-toimenpiteet (:toimenpiteet app) toimenpiteet)
               :siirto-kaynnissa? false))

  ToimenpiteetEiSiirretty
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden siirto epäonnistui!" :danger)
    (assoc app :siirto-kaynnissa? false))

  LisaaToimenpiteelleLiite
  (process-event [{tiedot :tiedot} app]
    (if (not (:liitteen-lisays-kaynnissa? app))
      (do (tuck-tyokalut/post! :lisaa-toimenpiteelle-liite
                               {::to/urakka-id (get-in app [:valinnat :urakka-id])
                                       ::to/liite-id (get-in tiedot [:liite :id])
                                       ::to/id (::to/id tiedot)}
                               {:onnistui ->LiiteLisatty
                                       :onnistui-parametrit [tiedot]
                                       :epaonnistui ->LiiteEiLisatty})
          (assoc app :liitteen-lisays-kaynnissa? true))
      app))

  LiiteLisatty
  (process-event [{vastaus :vastaus tiedot :tiedot} app]
    (let [liite (:liite tiedot)
          toimenpide-id (::to/id tiedot)]
      (assoc app :toimenpiteet (map (fn [toimenpide]
                                      (if (= (::to/id toimenpide) toimenpide-id)
                                        (assoc toimenpide ::to/liitteet (conj (::to/liitteet toimenpide)
                                                                              liite))
                                        toimenpide))
                                    (:toimenpiteet app))
                 :liitteen-lisays-kaynnissa? false)))

  LiiteEiLisatty
  (process-event [_ app]
    (viesti/nayta! "Liitteen lisäys epäonnistui!" :danger)
    (assoc app :liitteen-lisays-kaynnissa? false))

  PoistaToimenpiteenLiite
  (process-event [{tiedot :tiedot} app]
    (if (not (:liitteen-poisto-kaynnissa? app))
      (do (tuck-tyokalut/post! :poista-toimenpiteen-liite
                               {::to/urakka-id (get-in app [:valinnat :urakka-id])
                                       ::to/liite-id (::to/liite-id tiedot)
                                       ::to/id (::to/id tiedot)}
                               {:onnistui ->LiitePoistettu
                                       :onnistui-parametrit [tiedot]
                                       :epaonnistui ->LiiteEiPoistettu})
          (assoc app :liitteen-poisto-kaynnissa? true))
      app))

  LiitePoistettu
  (process-event [{vastaus :vastaus tiedot :tiedot} app]
    (let [liite-id (::to/liite-id tiedot)
          toimenpide-id (::to/id tiedot)]
      (assoc app :toimenpiteet (map (fn [toimenpide]
                                      (if (= (::to/id toimenpide) toimenpide-id)
                                        (assoc toimenpide ::to/liitteet (filter #(not= (:id %) liite-id)
                                                                                (::to/liitteet toimenpide)))
                                        toimenpide))
                                    (:toimenpiteet app))
                 :liitteen-poisto-kaynnissa? false)))

  LiiteEiPoistettu
  (process-event [_ app]
    (viesti/nayta! "Liitteen poistaminen epäonnistui!" :danger)
    (assoc app :liitteen-poisto-kaynnissa? false))

  HaeToimenpiteidenTurvalaitteetKartalle
  (process-event [{to :toimenpiteet} app]
    (if (not-empty to)
      (let [haettavat (into #{} (map (comp ::tu/turvalaitenro ::to/turvalaite) to))]
       (tuck-tyokalut/post! :hae-turvalaitteet-kartalle
                            {:turvalaitenumerot haettavat}
                            {:onnistui ->TurvalaitteetKartalleHaettu
                                    :onnistui-parametrit [haettavat]
                                    :epaonnistui ->TurvalaitteetKartalleEiHaettu
                                    :epaonnistui-parametrit [haettavat]})
       (assoc app :kartalle-haettavat-toimenpiteet haettavat))

      (assoc app :kartalle-haettavat-toimenpiteet nil
                 :turvalaitteet-kartalla nil
                 :turvalaitteet nil
                 :korostetut-turvalaitteet nil)))

  TurvalaitteetKartalleHaettu
  (process-event [{haetut :haetut tulos :tulos} {:keys [kartalle-haettavat-toimenpiteet] :as app}]
    ;; Jos valmistunut haku ei ole uusin aloitettu, ei tehdä mitään.
    (if (= haetut kartalle-haettavat-toimenpiteet)
      (assoc app :kartalle-haettavat-toimenpiteet nil
                 :turvalaitteet-kartalla (turvalaitteet-kartalle tulos app)
                 :turvalaitteet tulos
                 :korostetut-turvalaitteet nil)

      app))

  TurvalaitteetKartalleEiHaettu
  (process-event [{haetut :haetut} {:keys [kartalle-haettavat-toimenpiteet] :as app}]
    (if (= haetut kartalle-haettavat-toimenpiteet)
      (do (viesti/nayta! "Toimenpiteiden haku kartalle epäonnistui!" :danger)
          (assoc app :kartalle-haettavat-toimenpiteet nil
                     :turvalaitteet-kartalla nil
                     :turvalaitteet nil
                     :korostetut-turvalaitteet nil))

      app))

  KorostaToimenpideKartalla
  (process-event [{toimenpide :toimenpide fn :lisa-funktiot} app]
    (let [fn (or fn [])]
      ((apply comp fn)
        (korosta-kartalla #{(get-in toimenpide [::to/turvalaite ::tu/turvalaitenro])} app)))))
