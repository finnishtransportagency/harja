(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset
  (:require [reagent.core :refer [atom]]
            [tuck.core :as tuck]
            [harja.loki :refer [log error]]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.urakka :as ur]
            [harja.domain.vesivaylat.vayla :as va]
            [harja.domain.vesivaylat.turvalaite :as tu]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.hinta :as hinta]
            [harja.domain.vesivaylat.tyo :as tyo]
            [cljs.core.async :as async :refer [<!]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.tyokalut.tuck :as tuck-tyokalut]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.protokollat :as protokollat]
            [harja.ui.viesti :as viesti]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [cljs.spec.alpha :as s]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu]
            [harja.domain.urakka :as urakka]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def alustettu-toimenpiteen-hinnoittelu
  {::to/id nil
   ::h/hintaelementit nil
   ::h/tyot []})

(def alustettu-hintaryhman-hinnoittelu
  {::h/id nil
   ::h/hintaelementit nil})

(defonce tila
  (atom {:valinnat {:urakka-id nil
                    :sopimus-id nil
                    :aikavali [nil nil]
                    :vaylatyyppi nil
                    :vayla nil
                    :tyolaji nil
                    :tyoluokka nil
                    :toimenpide nil
                    :vain-vikailmoitukset? false}
         :nakymassa? false
         :toimenpiteiden-haku-kaynnissa? false
         :infolaatikko-nakyvissa {} ; tunniste -> boolean
         :uuden-hintaryhman-lisays? false
         :valittu-hintaryhma nil
         :uusi-hintaryhma ""
         :hintaryhman-tallennus-kaynnissa? false
         :hintaryhmien-poisto-kaynnissa? false
         :hintaryhmat nil
         :hintaryhmien-haku-kaynnissa? false
         :liitteen-lisays-kaynnissa? false
         :liitteen-poisto-kaynnissa? false
         :toimenpiteet nil
         :suunnitellut-tyot nil
         :suunniteltujen-toiden-haku-kaynnissa? false
         :hintaryhmien-liittaminen-kaynnissa? false
         :toimenpiteen-hinnoittelun-tallennus-kaynnissa? false
         :hintaryhman-hinnoittelun-tallennus-kaynnissa? false
         :hinnoittele-toimenpide alustettu-toimenpiteen-hinnoittelu
         :hinnoittele-hintaryhma alustettu-hintaryhman-hinnoittelu
         :turvalaitteet-kartalla nil
         :karttataso-nakyvissa? true
         :korostetut-turvalaitteet nil
         ;; korostettu-hintaryhma on false, kun hintaryhmää ei ole korostettu,
         ;; koska "kokonaishintaisista siirrettyjen" hintaryhmän id:n täytyy olla nil
         :korostettu-hintaryhma false}))

(defonce karttataso-yksikkohintaisten-turvalaitteet (r/cursor tila [:karttataso-nakyvissa?]))
(defonce turvalaitteet-kartalla (r/cursor tila [:turvalaitteet-kartalla]))

(def valinnat
  (reaction
    (when (:nakymassa? @tila)
      {:urakka-id (:id @nav/valittu-urakka)
       :sopimus-id (first @u/valittu-sopimusnumero)
       :aikavali @u/valittu-aikavali})))

(def vaylahaku
  (reify protokollat/Haku
    (hae [_ teksti]
      (go (let [vastaus (<! (k/post! :hae-vaylat {:hakuteksti teksti
                                                  :vaylatyyppi (get-in @tila [:valinnat :vaylatyyppi])}))]
            vastaus)))))

(defn hintaryhma-korostettu? [hintaryhma {:keys [korostettu-hintaryhma]}]
  (boolean
    (when-not (false? korostettu-hintaryhma)
      (= (::h/id hintaryhma) korostettu-hintaryhma))))

(defn kokonaishintaisista-siirretyt-hintaryhma []
  [{::h/nimi "Kokonaishintaisista siirretyt, valitse tilaus."
    ::h/id nil}])

(defn kokonaishintaisista-siirretyt-hintaryhma? [hintaryhma]
  (= (::h/id hintaryhma) (::h/id (kokonaishintaisista-siirretyt-hintaryhma))))

(defn poista-hintaryhmien-korostus [app]
  (assoc app :korostettu-hintaryhma false))

(defrecord Nakymassa? [nakymassa?])
(defrecord TyhjennaSuunnitellutTyot [])
(defrecord PaivitaValinnat [tiedot])
(defrecord HaeToimenpiteet [valinnat])
(defrecord HaeSuunnitellutTyot [])
(defrecord SuunnitellutTyotHaettu [vastaus])
(defrecord SuunnitellutTyotEiHaettu [virhe])
(defrecord ToimenpiteetHaettu [toimenpiteet])
(defrecord ToimenpiteetEiHaettu [virhe])
(defrecord UudenHintaryhmanLisays? [lisays-auki?])
(defrecord UudenHintaryhmanNimeaPaivitetty [nimi])
(defrecord SiirraValitutKokonaishintaisiin [])
(defrecord LuoHintaryhma [nimi])
(defrecord HintaryhmaLuotu [vastaus])
(defrecord HintaryhmaEiLuotu [virhe])
(defrecord HaeHintaryhmat [])
(defrecord HintaryhmatHaettu [vastaus])
(defrecord HintaryhmatEiHaettu [virhe])
(defrecord ValitseHintaryhma [hintaryhma])
(defrecord LiitaValitutHintaryhmaan [hintaryhma valitut])
(defrecord ValitutLiitettyHintaryhmaan [])
(defrecord ValitutEiLiitettyHintaryhmaan [virhe])
(defrecord AloitaToimenpiteenHinnoittelu [toimenpide-id])
(defrecord AloitaHintaryhmanHinnoittelu [hintaryhma-id])
(defrecord HinnoitteleToimenpideKentta [tiedot])
(defrecord HinnoitteleHintaryhmaKentta [tiedot])
(defrecord HinnoitteleToimenpide [tiedot])
(defrecord HinnoitteleTyo [tiedot])
(defrecord AsetaTyolleTehtava [tiedot])
(defrecord HinnoitteleHintaryhma [tiedot])
(defrecord ToimenpiteenHinnoitteluTallennettu [vastaus])
(defrecord ToimenpiteenHinnoitteluEiTallennettu [virhe])
(defrecord HintaryhmanHinnoitteluTallennettu [vastaus])
(defrecord HintaryhmanHinnoitteluEiTallennettu [virhe])
(defrecord PeruToimenpiteenHinnoittelu [])
(defrecord PeruHintaryhmanHinnoittelu [])
(defrecord PoistaHintaryhmat [hintaryhma-idt])
(defrecord HintaryhmatPoistettu [vastaus])
(defrecord HintaryhmatEiPoistettu [])
(defrecord KorostaHintaryhmaKartalla [hintaryhma])
(defrecord PoistaHintaryhmanKorostus [])
(defrecord LisaaHinnoiteltavaTyorivi [])
(defrecord PoistaHinnoiteltavaTyorivi [tiedot])

(defn- hintakentta [otsikko hinta]
  {::hinta/id (::hinta/id hinta)
   ::hinta/otsikko otsikko
   ::hinta/maara (or (::hinta/maara hinta) 0)
   ::hinta/yleiskustannuslisa (if-let [yleiskustannuslisa (::hinta/yleiskustannuslisa hinta)]
                                yleiskustannuslisa
                                0)})

(defn- toimenpiteen-hintakentat [hinnat]
  [(hintakentta "Työ" (hinta/hinta-otsikolla "Työ" hinnat))
   (hintakentta "Komponentit" (hinta/hinta-otsikolla "Komponentit" hinnat))
   (hintakentta "Yleiset materiaalit" (hinta/hinta-otsikolla "Yleiset materiaalit" hinnat))
   (hintakentta "Matkakulut" (hinta/hinta-otsikolla "Matkakulut" hinnat))
   (hintakentta "Muut kulut" (hinta/hinta-otsikolla "Muut kulut" hinnat))])

(def hintaryhman-hintakentta-otsikko "Ryhmähinta")

(defn- hintaryhman-hintakentat [hinnat]
  [(hintakentta hintaryhman-hintakentta-otsikko (hinta/hinta-otsikolla hintaryhman-hintakentta-otsikko hinnat))])

(extend-protocol tuck/Event

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?
               :karttataso-nakyvissa? nakymassa?))

  TyhjennaSuunnitellutTyot
  (process-event [_ app]
    ;; TODO TESTI
    (assoc app :suunnitellut-tyot nil))

  PaivitaValinnat
  ;; Valintojen päivittäminen laukaisee aina myös kantahaun uusimmilla valinnoilla (ellei ole jo käynnissä),
  ;; jotta näkymä pysyy synkassa valintojen kanssa
  (process-event [{tiedot :tiedot} app]
    (let [uudet-valinnat (merge (:valinnat app)
                                (select-keys tiedot jaettu/valintojen-avaimet))
          haku (tuck/send-async! ->HaeToimenpiteet)]
      (go (haku uudet-valinnat))
      (assoc app :valinnat uudet-valinnat)))

  SiirraValitutKokonaishintaisiin
  (process-event [_ app]
    (jaettu/siirra-valitut! :siirra-toimenpiteet-kokonaishintaisiin app))

  HaeToimenpiteet
  (process-event [{valinnat :valinnat} app]
    (if (and (not (:toimenpiteiden-haku-kaynnissa? app))
             (some? (:urakka-id valinnat)))
      (do (tuck-tyokalut/palvelukutsu :hae-yksikkohintaiset-toimenpiteet
                                      (jaettu/toimenpiteiden-hakukyselyn-argumentit valinnat)
                                      {:onnistui ->ToimenpiteetHaettu
                                       :epaonnistui ->ToimenpiteetEiHaettu})
          (assoc app :toimenpiteiden-haku-kaynnissa? true))
      app))

  ToimenpiteetHaettu
  (process-event [{toimenpiteet :toimenpiteet} app]
    (let [turvalaitteet-kartalle (tuck/send-async! jaettu/->HaeToimenpiteidenTurvalaitteetKartalle)]
      (go (turvalaitteet-kartalle toimenpiteet))
      (assoc app :toimenpiteet (jaettu/toimenpiteet-aikajarjestyksessa toimenpiteet)
                 :toimenpiteiden-haku-kaynnissa? false)))

  ToimenpiteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden haku epäonnistui!" :danger)
    (assoc app :toimenpiteiden-haku-kaynnissa? false))

  HaeSuunnitellutTyot
  (process-event [_ app]
    ;; TODO TESTI
    (let [urakka-id (get-in app [:valinnat :urakka-id])]
      (if (and (not (:suunniteltujen-toiden-haku-kaynnissa? app)) (some? urakka-id))
        (do (tuck-tyokalut/palvelukutsu :yksikkohintaiset-tyot
                                        {:urakka urakka-id}
                                        {:onnistui ->SuunnitellutTyotHaettu
                                         :epaonnistui ->SuunnitellutTyotEiHaettu})
            (assoc app :suunniteltujen-toiden-haku-kaynnissa? true))
        app)))

  SuunnitellutTyotHaettu
  (process-event [{vastaus :vastaus} app]
    ;; TODO TESTI
    (assoc app :suunnitellut-tyot vastaus
               :suunniteltujen-toiden-haku-kaynnissa? false))

  SuunnitellutTyotEiHaettu
  (process-event [_ app]
    ;; TODO TESTI
    (viesti/nayta! "Suunniteltujen töiden haku epäonnistui!" :danger)
    (assoc app :suunniteltujen-toiden-haku-kaynnissa? false))

  UudenHintaryhmanLisays?
  (process-event [{lisays-auki? :lisays-auki?} app]
    (assoc app :uuden-hintaryhman-lisays? lisays-auki?))

  UudenHintaryhmanNimeaPaivitetty
  (process-event [{nimi :nimi} app]
    (assoc app :uusi-hintaryhma nimi))

  LuoHintaryhma
  (process-event [{nimi :nimi} app]
    (if-not (:hintaryhman-tallennus-kaynnissa? app)
      (do (tuck-tyokalut/palvelukutsu :luo-hinnoittelu
                                      {::h/nimi nimi
                                       ::urakka/id (get-in app [:valinnat :urakka-id])}
                                      {:onnistui ->HintaryhmaLuotu
                                       :epaonnistui ->HintaryhmaEiLuotu})
          (assoc app :hintaryhman-tallennus-kaynnissa? true))
      app))

  HintaryhmaLuotu
  (process-event [{vastaus :vastaus} app]
    (-> app
        (update :hintaryhmat conj vastaus)
        (assoc :hintaryhman-tallennus-kaynnissa? false
               :uusi-hintaryhma nil
               :uuden-hintaryhman-lisays? false)))

  HintaryhmaEiLuotu
  (process-event [_ app]
    (viesti/nayta! "Tilauksen tallennus epäonnistui!" :danger)
    (assoc app :hintaryhman-tallennus-kaynnissa? false
               :uusi-hintaryhma nil
               :uuden-hintaryhman-lisays? false))

  HaeHintaryhmat
  (process-event [_ app]
    (if-not (:hintaryhmien-haku-kaynnissa? app)
      (do (tuck-tyokalut/palvelukutsu :hae-hinnoittelut
                                      {::urakka/id (get-in app [:valinnat :urakka-id])}
                                      {:onnistui ->HintaryhmatHaettu
                                       :epaonnistui ->HintaryhmatEiHaettu})
          (assoc app :hintaryhmien-haku-kaynnissa? true))
      app))

  HintaryhmatHaettu
  (process-event [{vastaus :vastaus} app]
    (assoc app :hintaryhmat vastaus
               :hintaryhmien-haku-kaynnissa? false))

  HintaryhmatEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Tilauksien haku epäonnistui!" :danger)
    (assoc app :hintaryhmien-haku-kaynnissa? false))

  ValitseHintaryhma
  (process-event [{hintaryhma :hintaryhma} app]
    (assoc app :valittu-hintaryhma hintaryhma))

  LiitaValitutHintaryhmaan
  (process-event [{hintaryhma :hintaryhma valitut :valitut} app]
    (if-not (:hintaryhmien-liittaminen-kaynnissa? app)
      (do (tuck-tyokalut/palvelukutsu :liita-toimenpiteet-hinnoitteluun
                                      {::to/idt (map ::to/id valitut)
                                       ::h/id (::h/id hintaryhma)
                                       ::urakka/id (get-in app [:valinnat :urakka-id])}
                                      {:onnistui ->ValitutLiitettyHintaryhmaan
                                       :epaonnistui ->ValitutEiLiitettyHintaryhmaan})
          (assoc app :hintaryhmien-liittaminen-kaynnissa? true))
      app))

  ValitutLiitettyHintaryhmaan
  (process-event [_ app]
    (let [toimenpidehaku (tuck/send-async! ->HaeToimenpiteet)
          hintaryhmahaku (tuck/send-async! ->HaeHintaryhmat)]
      (go (toimenpidehaku (:valinnat app)))
      (go (hintaryhmahaku nil)) ;; Tarvitaan tieto siitä, miten tieto tyhjistä hintaryhmistä muuttuu
      (assoc app :hintaryhmien-liittaminen-kaynnissa? false)))

  ValitutEiLiitettyHintaryhmaan
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden liittäminen tilaukseen epäonnistui!" :danger)
    (assoc app :hintaryhmien-liittaminen-kaynnissa? false))

  AloitaToimenpiteenHinnoittelu
  (process-event [{toimenpide-id :toimenpide-id} app]
    (let [hinnoiteltava-toimenpide (to/toimenpide-idlla (:toimenpiteet app) toimenpide-id)
          toimenpiteen-oma-hinnoittelu (::to/oma-hinnoittelu hinnoiteltava-toimenpide)
          hinnat (::h/hinnat toimenpiteen-oma-hinnoittelu)]
      (assoc app :hinnoittele-toimenpide
                 {::to/id toimenpide-id
                  ::h/hintaelementit (toimenpiteen-hintakentat hinnat)})))

  AloitaHintaryhmanHinnoittelu
  (process-event [{hintaryhma-id :hintaryhma-id} app]
    (let [hinnoiteltava-hintaryhma (h/hinnoittelu-idlla (:hintaryhmat app) hintaryhma-id)
          hinnat (::h/hinnat hinnoiteltava-hintaryhma)]
      (assoc app :hinnoittele-hintaryhma
                 {::h/id hintaryhma-id
                  ::h/hintaelementit (hintaryhman-hintakentat hinnat)})))

  HinnoitteleToimenpideKentta
  (process-event [{tiedot :tiedot} app]
    (assoc-in app [:hinnoittele-toimenpide ::h/hintaelementit]
              (hinta/paivita-hintajoukon-hinnan-tiedot-otsikolla (get-in app [:hinnoittele-toimenpide
                                                                              ::h/hintaelementit]) tiedot)))

  HinnoitteleHintaryhmaKentta
  (process-event [{tiedot :tiedot} app]
    (assoc-in app [:hinnoittele-hintaryhma ::h/hintaelementit]
              (hinta/paivita-hintajoukon-hinnan-tiedot-otsikolla (get-in app [:hinnoittele-hintaryhma
                                                                              ::h/hintaelementit]) tiedot)))

  HinnoitteleTyo
  (process-event [{tiedot :tiedot} app]
    (assoc-in app [:hinnoittele-toimenpide ::h/tyot]
              (tyo/paivita-tyojoukon-tyon-tiedot-idlla (get-in app [:hinnoittele-toimenpide
                                                                    ::h/tyot]) tiedot)))

  AsetaTyolleTehtava
  (process-event [{tiedot :tiedot} app]
    ;; TODO TESTI
    (log "[DEBUG] ASETA TYÖLLE TEHTÄVÄ, TIEDOT: " (pr-str tiedot))
    (assoc-in app [:hinnoittele-toimenpide ::h/tyot]
              (tyo/paivita-tyojoukon-tyon-tiedot-idlla (get-in app [:hinnoittele-toimenpide
                                                                    ::h/tyot]) tiedot)))
  HinnoitteleToimenpide
  (process-event [{tiedot :tiedot} app]
    (if-not (:toimenpiteen-hinnoittelun-tallennus-kaynnissa? app)
      (do (tuck-tyokalut/palvelukutsu :tallenna-toimenpiteelle-hinta
                                      {::to/urakka-id (get-in app [:valinnat :urakka-id])
                                       ::to/id (get-in app [:hinnoittele-toimenpide ::to/id])
                                       ::h/hintaelementit (mapv
                                                            (fn [hinta]
                                                              (merge
                                                                (when-let [id (::hinta/id hinta)]
                                                                  {::hinta/id id})
                                                                {::hinta/otsikko (::hinta/otsikko hinta)
                                                                 ::hinta/maara (::hinta/maara hinta)
                                                                 ::hinta/yleiskustannuslisa (::hinta/yleiskustannuslisa hinta)}))
                                                            (get-in app [:hinnoittele-toimenpide ::h/hintaelementit]))}
                                      {:onnistui ->ToimenpiteenHinnoitteluTallennettu
                                       :epaonnistui ->ToimenpiteenHinnoitteluEiTallennettu})
          (assoc app :toimenpiteen-hinnoittelun-tallennus-kaynnissa? true))
      app))

  HinnoitteleHintaryhma
  (process-event [{tiedot :tiedot} app]
    (if-not (:hintaryhman-hinnoittelun-tallennus-kaynnissa? app)
      (do (tuck-tyokalut/palvelukutsu :tallenna-hintaryhmalle-hinta
                                      {::ur/id (get-in app [:valinnat :urakka-id])
                                       ::h/id (get-in app [:hinnoittele-hintaryhma ::h/id])
                                       ::h/hintaelementit (mapv
                                                            (fn [hinta]
                                                              (merge
                                                                (when-let [id (::hinta/id hinta)]
                                                                  {::hinta/id id})
                                                                {::hinta/otsikko (::hinta/otsikko hinta)
                                                                 ::hinta/maara (::hinta/maara hinta)
                                                                 ::hinta/yleiskustannuslisa (::hinta/yleiskustannuslisa hinta)}))
                                                            (get-in app [:hinnoittele-hintaryhma ::h/hintaelementit]))}
                                      {:onnistui ->HintaryhmanHinnoitteluTallennettu
                                       :epaonnistui ->HintaryhmanHinnoitteluEiTallennettu})
          (assoc app :hintaryhman-hinnoittelun-tallennus-kaynnissa? true))
      app))

  ToimenpiteenHinnoitteluTallennettu
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Hinnoittelu tallennettu!" :success)
    (let [paivitettava-toimenpide (to/toimenpide-idlla (:toimenpiteet app)
                                                       (get-in app [:hinnoittele-toimenpide ::to/id]))
          paivitetty-toimenpide (assoc paivitettava-toimenpide ::to/oma-hinnoittelu vastaus)
          paivitetyt-toimenpiteet (mapv
                                    (fn [toimenpide]
                                      (if (= (::to/id toimenpide) (::to/id paivitettava-toimenpide))
                                        paivitetty-toimenpide
                                        toimenpide))
                                    (:toimenpiteet app))]
      (assoc app :toimenpiteet paivitetyt-toimenpiteet
                 :toimenpiteen-hinnoittelun-tallennus-kaynnissa? false
                 :hinnoittele-toimenpide alustettu-toimenpiteen-hinnoittelu)))

  ToimenpiteenHinnoitteluEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Hinnoittelun tallennus epäonnistui!" :danger)
    (assoc app :toimenpiteen-hinnoittelun-tallennus-kaynnissa? false))

  HintaryhmanHinnoitteluTallennettu
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Hinnoittelu tallennettu!" :success)
    (assoc app :hintaryhmat vastaus
               :hintaryhman-hinnoittelun-tallennus-kaynnissa? false
               :hinnoittele-hintaryhma alustettu-hintaryhman-hinnoittelu))

  HintaryhmanHinnoitteluEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Hinnoittelun tallennus epäonnistui!" :danger)
    (assoc app :hintaryhman-hinnoittelun-tallennus-kaynnissa? false))

  PeruToimenpiteenHinnoittelu
  (process-event [_ app]
    (assoc app :toimenpiteen-hinnoittelun-tallennus-kaynnissa? false
               :hinnoittele-toimenpide alustettu-toimenpiteen-hinnoittelu))

  PeruHintaryhmanHinnoittelu
  (process-event [_ app]
    (assoc app :hintaryhman-hinnoittelun-tallennus-kaynnissa? false
               :hinnoittele-hintaryhma alustettu-hintaryhman-hinnoittelu))

  PoistaHintaryhmat
  (process-event [{hintaryhma-idt :hintaryhma-idt} app]
    (if-not (:hintaryhmien-poisto-kaynnissa? app)
      (do (tuck-tyokalut/palvelukutsu :poista-tyhjat-hinnoittelut
                                      {::h/urakka-id (get-in app [:valinnat :urakka-id])
                                       ::h/idt hintaryhma-idt}
                                      {:onnistui ->HintaryhmatPoistettu
                                       :epaonnistui ->HintaryhmatEiPoistettu})
          (assoc app :hintaryhmien-poisto-kaynnissa? true))
      app))

  HintaryhmatPoistettu
  (process-event [{vastaus :vastaus} app]
    (assoc app :hintaryhmien-poisto-kaynnissa? false
               :hintaryhmat (h/hinnoittelut-ilman (:hintaryhmat app)
                                                  (::h/idt vastaus))))

  HintaryhmatEiPoistettu
  (process-event [_ app]
    (viesti/nayta! "Tilauksen poisto epäonnistui!" :danger)
    (assoc app :hintaryhmien-poisto-kaynnissa? false))

  KorostaHintaryhmaKartalla
  (process-event [{hintaryhma :hintaryhma} {:keys [toimenpiteet] :as app}]
    (let [korostettavat-turvalaitteet (->>
                                        toimenpiteet
                                        (filter #(= (::to/hintaryhma-id %) (::h/id hintaryhma)))
                                        (map (comp ::tu/turvalaitenro ::to/turvalaite))
                                        (into #{}))]
      (-> (jaettu/korosta-kartalla korostettavat-turvalaitteet app)
          (assoc :korostettu-hintaryhma (::h/id hintaryhma)))))

  LisaaHinnoiteltavaTyorivi
  (process-event [_ app]
    ;; TODO TESTI
    (let [tyot (get-in app [:hinnoittele-toimenpide ::h/tyot])
          seuraava-vapaa-neg-id (dec (apply min (concat [-1] (keep ::tyo/id tyot))))
          paivitetyt-tyot (conj tyot {::tyo/id seuraava-vapaa-neg-id})]
      (assoc-in app [:hinnoittele-toimenpide ::h/tyot] paivitetyt-tyot)))

  PoistaHinnoiteltavaTyorivi
  (process-event [{tiedot :tiedot} app]
    ;; TODO TESTI
    (let [tyot (get-in app [:hinnoittele-toimenpide ::h/tyot])
          tyot-ilman-poistettua (filter #(not= (::tyo/id %) (::tyo/id tiedot)) tyot)]
      (assoc-in app [:hinnoittele-toimenpide ::h/tyot] tyot-ilman-poistettua)))

  PoistaHintaryhmanKorostus
  (process-event [_ app]
    (->> app
         (poista-hintaryhmien-korostus)
         (jaettu/korosta-kartalla nil))))