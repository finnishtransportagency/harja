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
            [cljs.core.async :as async :refer [<!]]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.protokollat :as protokollat]
            [harja.ui.viesti :as viesti]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [cljs.spec.alpha :as s]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu]
            [harja.domain.urakka :as urakka])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def yleiskustannuslisa 12)

(defonce tila
  (atom {:valinnat {:urakka-id nil
                    :sopimus-id nil
                    :aikavali [nil nil]
                    :vaylatyyppi :kauppamerenkulku
                    :vayla nil
                    :tyolaji nil
                    :tyoluokka nil
                    :toimenpide nil
                    :vain-vikailmoitukset? false}
         :nakymassa? false
         :haku-kaynnissa? false
         :infolaatikko-nakyvissa? false
         :uuden-hintaryhman-lisays? false
         :valittu-hintaryhma nil
         :uusi-hintaryhma ""
         :hintaryhman-tallennus-kaynnissa? false
         :hintaryhmat nil
         :hintaryhmien-haku-kaynnissa? false
         :toimenpiteet nil
         :hintaryhmien-liittaminen-kaynnissa? false
         :hinnoittelun-tallennus-kaynnissa? false
         :hinnoittele-toimenpide {::to/id nil
                                  ::h/hintaelementit nil}}))

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

(defrecord Nakymassa? [nakymassa?])
(defrecord PaivitaValinnat [tiedot])
(defrecord HaeToimenpiteet [valinnat])
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
(defrecord ValitutLiitetty [vastaus])
(defrecord ValitutEiLiitetty [virhe])
(defrecord AloitaToimenpiteenHinnoittelu [toimenpide-id])
(defrecord HinnoitteleToimenpideKentta [tiedot])
(defrecord HinnoitteleToimenpide [tiedot])
(defrecord ToimenpiteenHinnoitteluTallennettu [vastaus])
(defrecord ToimenpiteenHinnoitteluEiTallennettu [virhe])
(defrecord PeruToimenpiteenHinnoittelu [])

(def alustettu-toimenpiteen-hinnoittelu
  {:hinnoittelun-tallennus-kaynnissa? false
   :hinnoittele-toimenpide
   {::to/id nil
    ::h/hintaelementit nil}})

(defn kyselyn-hakuargumentit [valinnat]
  (merge (jaettu/kyselyn-hakuargumentit valinnat) {:tyyppi :yksikkohintainen}))

(extend-protocol tuck/Event

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?))

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
  ;; Hakee toimenpiteet annetuilla valinnoilla. Jos valintoja ei anneta, käyttää tilassa olevia valintoja.
  (process-event [{valinnat :valinnat} app]
    (if-not (:haku-kaynnissa? app)
      (let [tulos! (tuck/send-async! ->ToimenpiteetHaettu)
            fail! (tuck/send-async! ->ToimenpiteetEiHaettu)]
        (try
          (let [hakuargumentit (kyselyn-hakuargumentit valinnat)]
            (go
              (let [vastaus (<! (k/post! :hae-yksikkohintaiset-toimenpiteet hakuargumentit))]
                (if (k/virhe? vastaus)
                  (fail! vastaus)
                  (tulos! vastaus))))
            (assoc app :haku-kaynnissa? true))
          (catch :default e
            (fail! nil)
            (throw e))))

      app))

  ToimenpiteetHaettu
  (process-event [{toimenpiteet :toimenpiteet} app]
    (assoc app :toimenpiteet (jaettu/toimenpiteet-aikajarjestyksessa toimenpiteet)
               :haku-kaynnissa? false))

  ToimenpiteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden haku epäonnistui!" :danger)
    (assoc app :haku-kaynnissa? false))

  UudenHintaryhmanLisays?
  (process-event [{lisays-auki? :lisays-auki?} app]
    (assoc app :uuden-hintaryhman-lisays? lisays-auki?))

  UudenHintaryhmanNimeaPaivitetty
  (process-event [{nimi :nimi} app]
    (assoc app :uusi-hintaryhma nimi))

  LuoHintaryhma
  (process-event [{nimi :nimi} app]
    (if-not (:hintaryhman-tallennus-kaynnissa? app)
      (let [tulos! (tuck/send-async! ->HintaryhmaLuotu)
            fail! (tuck/send-async! ->HintaryhmaEiLuotu)
            parametrit {::h/nimi nimi
                        ::urakka/id (get-in app [:valinnat :urakka-id])}]
        (try
          (go
            (let [vastaus (<! (k/post! :luo-hinnoittelu parametrit))]
              (if (k/virhe? vastaus)
                (fail! vastaus)
                (tulos! vastaus))))
          (assoc app :hintaryhman-tallennus-kaynnissa? true)
          (catch :default e
            (fail! nil)
            (throw e))))

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
    (viesti/nayta! "Hintaryhmän tallennus epäonnistui!" :danger)
    (assoc app :hintaryhman-tallennus-kaynnissa? false
               :uusi-hintaryhma nil
               :uuden-hintaryhman-lisays? false))

  HaeHintaryhmat
  (process-event [_ app]
    (if-not (:hintaryhmien-haku-kaynnissa? app)
      (when-let [urakka-id (get-in app [:valinnat :urakka-id])]
        (let [tulos! (tuck/send-async! ->HintaryhmatHaettu)
             fail! (tuck/send-async! ->HintaryhmatEiHaettu)
             parametrit {::urakka/id urakka-id}]
         (try
           (go
             (let [vastaus (<! (k/post! :hae-hinnoittelut parametrit))]
               (if (k/virhe? vastaus)
                 (fail! vastaus)
                 (tulos! vastaus))))
           (assoc app :hintaryhmien-haku-kaynnissa? true)
           (catch :default e
             (fail! nil)
             (throw e)))))

      app))

  HintaryhmatHaettu
  (process-event [{vastaus :vastaus} app]
    (assoc app :hintaryhmat vastaus
               :hintaryhmien-haku-kaynnissa? false))

  HintaryhmatEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Hintaryhmien haku epäonnistui!" :danger)
    (assoc app :hintaryhmien-haku-kaynnissa? false))

  ValitseHintaryhma
  (process-event [{hintaryhma :hintaryhma} app]
    (assoc app :valittu-hintaryhma hintaryhma))

  LiitaValitutHintaryhmaan
  (process-event [{hintaryhma :hintaryhma valitut :valitut} app]
    (if-not (:hintaryhmien-liittaminen-kaynnissa? app)
      (let [tulos! (tuck/send-async! ->ValitutLiitetty)
            fail! (tuck/send-async! ->ValitutEiLiitetty)
            parametrit {::to/idt (map ::to/id valitut)
                        ::h/id (::h/id hintaryhma)
                        ::urakka/id (get-in app [:valinnat :urakka-id])}]
        (try
          (go
            (let [vastaus (<! (k/post! :liita-toimenpiteet-hinnoitteluun parametrit))]
              (if (k/virhe? vastaus)
                (fail! vastaus)
                (tulos! vastaus))))
          (assoc app :hintaryhmien-liittaminen-kaynnissa? true)

          (catch :default e
            (fail! nil)
            (throw e))))

      app))

  ValitutLiitetty
  (process-event [{vastaus :vastaus} app]
    (let [haku (tuck/send-async! ->HaeToimenpiteet)]
      (go (haku (:valinnat app)))

      (assoc app :hintaryhmien-liittaminen-kaynnissa? false)))

  ValitutEiLiitetty
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden liittäminen hintaryhmiin epäonnistui!" :danger)
    (assoc app :hintaryhmien-liittaminen-kaynnissa? false))

  AloitaToimenpiteenHinnoittelu
  (process-event [{toimenpide-id :toimenpide-id} app]
    (let [hinnoiteltava-toimenpide (to/toimenpide-idlla (:toimenpiteet app) toimenpide-id)
          toimenpiteen-oma-hinnoittelu (::to/oma-hinnoittelu hinnoiteltava-toimenpide)
          hinnat (::h/hinnat toimenpiteen-oma-hinnoittelu)
          luo-hinta (fn [otsikko olemassa-oleva-hinta]
                      {::hinta/id (::hinta/id olemassa-oleva-hinta)
                       ::hinta/otsikko otsikko
                       ::hinta/maara (or (::hinta/maara olemassa-oleva-hinta) 0)
                       ::hinta/yleiskustannuslisa (if-let [yleiskustannuslisa (::hinta/yleiskustannuslisa olemassa-oleva-hinta)]
                                                    (not (zero? yleiskustannuslisa))
                                                    false)})]
      (assoc app :hinnoittele-toimenpide
                 {::to/id toimenpide-id
                  ::h/hintaelementit
                  [(luo-hinta "Työ" (hinta/hinta-otsikolla "Työ" hinnat))
                   (luo-hinta "Komponentit" (hinta/hinta-otsikolla "Komponentit" hinnat))
                   (luo-hinta "Yleiset materiaalit" (hinta/hinta-otsikolla "Yleiset materiaalit" hinnat))
                   (luo-hinta "Matkat" (hinta/hinta-otsikolla "Matkat" hinnat))
                   (luo-hinta "Muut kulut" (hinta/hinta-otsikolla "Muut kulut" hinnat))]})))

  HinnoitteleToimenpideKentta
  (process-event [{tiedot :tiedot} app]
    (assoc-in app [:hinnoittele-toimenpide ::h/hintaelementit]
              (mapv (fn [hinnoittelu]
                      (if (= (::hinta/otsikko hinnoittelu) (::hinta/otsikko tiedot))
                        (cond-> hinnoittelu
                                (::hinta/maara tiedot)
                                (assoc ::hinta/maara (::hinta/maara tiedot))

                                (some? (::hinta/yleiskustannuslisa tiedot))
                                (assoc ::hinta/yleiskustannuslisa (::hinta/yleiskustannuslisa tiedot)))
                        hinnoittelu))
                    (get-in app [:hinnoittele-toimenpide ::h/hintaelementit]))))

  HinnoitteleToimenpide
  (process-event [{tiedot :tiedot} app]
    (if-not (:hinnoittelun-tallennus-kaynnissa? app)
      (let [tulos! (tuck/send-async! ->ToimenpiteenHinnoitteluTallennettu)
            fail! (tuck/send-async! ->ToimenpiteenHinnoitteluEiTallennettu)
            parametrit {::to/urakka-id (get-in app [:valinnat :urakka-id])
                        ::to/id (get-in app [:hinnoittele-toimenpide ::to/id])
                        ::h/hintaelementit (mapv
                                             (fn [hinta]
                                               (merge
                                                 (when-let [id (::hinta/id hinta)]
                                                   {::hinta/id id})
                                                 {::hinta/otsikko (::hinta/otsikko hinta)
                                                  ::hinta/maara (::hinta/maara hinta)
                                                  ::hinta/yleiskustannuslisa (if (::hinta/yleiskustannuslisa hinta)
                                                                               yleiskustannuslisa
                                                                               0)}))
                                             (get-in app [:hinnoittele-toimenpide ::h/hintaelementit]))}]
        (try
          (go
            (let [vastaus (<! (k/post! :tallenna-toimenpiteelle-hinta parametrit))]
              (if (k/virhe? vastaus)
                (fail! vastaus)
                (tulos! vastaus))))
          (assoc app :hinnoittelun-tallennus-kaynnissa? true)

          (catch :default e
            (fail! nil)
            (throw e))))

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
      (merge (assoc app :toimenpiteet paivitetyt-toimenpiteet)
             alustettu-toimenpiteen-hinnoittelu)))

  ToimenpiteenHinnoitteluEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Hinnoittelun tallennus epäonnistui!" :danger)
    (assoc app :hinnoittelun-tallennus-kaynnissa? false))

  PeruToimenpiteenHinnoittelu
  (process-event [_ app]
    (merge app alustettu-toimenpiteen-hinnoittelu)))