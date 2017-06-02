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
                                  :tyo 0
                                  :komponentit 0
                                  :yleiset-materiaalit 0
                                  :matkat 0
                                  :muut-kulut 0}}))

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
(defrecord HinnoitteluTallennettu [vastaus])
(defrecord HinnoitteluEiTallennettu [virhe])

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
    (assoc app :toimenpiteet toimenpiteet
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
      (let [tulos! (tuck/send-async! ->HintaryhmatHaettu)
            fail! (tuck/send-async! ->HintaryhmatEiHaettu)
            parametrit {::urakka/id (get-in app [:valinnat :urakka-id])}]
        (try
          (go
            (let [vastaus (<! (k/post! :hae-hinnoittelut parametrit))]
              (if (k/virhe? vastaus)
                (fail! vastaus)
                (tulos! vastaus))))
          (assoc app :hintaryhmien-haku-kaynnissa? true)
          (catch :default e
            (fail! nil)
            (throw e))))

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
    (go ((tuck/send-async! ->HaeToimenpiteet) (:valinnat app)))

    (assoc app :hintaryhmien-liittaminen-kaynnissa? false))

  ValitutEiLiitetty
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden liittäminen hintaryhmiin epäonnistui!" :danger)
    (assoc app :hintaryhmien-liittaminen-kaynnissa? false))

  AloitaToimenpiteenHinnoittelu
  (process-event [{toimenpide-id :toimenpide-id} app]
    (assoc app :hinnoittele-toimenpide {::to/id toimenpide-id
                                        :tyo 0
                                        :komponentit 0
                                        :yleiset-materiaalit 0
                                        :matkat 0
                                        :muut-kulut 0}))

  HinnoitteleToimenpideKentta
  (process-event [{tiedot :tiedot} app]
    (assoc-in app [:hinnoittele-toimenpide (:tunniste tiedot)] (:arvo tiedot)))

  HinnoitteleToimenpide
  (process-event [{tiedot :tiedot} app]
    (if-not (:hinnoittelun-tallennus-kaynnissa? app)
      (let [tulos! (tuck/send-async! ->HinnoitteluTallennettu)
            fail! (tuck/send-async! ->HinnoitteluEiTallennettu)
            parametrit (merge {::to/urakka-id (get-in app [:valinnat :urakka-id])}
                              (:hinnoittele-toimenpide app))]
        (try
          (go
            (let [vastaus (<! (k/post! :hinnoittele-toimenpide parametrit))]
              (if (k/virhe? vastaus)
                (fail! vastaus)
                (tulos! vastaus))))
          (assoc app :hinnoittelun-tallennus-kaynnissa? true)

          (catch :default e
            (fail! nil)
            (throw e))))

      app))

  HinnoitteluTallennettu
  (process-event [{vastaus :vastaus} app]
    (viesti/nayta! "Hinnoittelu tallennettu!" :success)
    (assoc app :hinnoittelun-tallennus-kaynnissa? false))

  HinnoitteluEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Hinnoittelun tallennus epäonnistui!" :danger)
    (assoc app :hinnoittelun-tallennus-kaynnissa? false)))

