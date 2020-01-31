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
            [harja.domain.vesivaylat.turvalaitekomponentti :as tkomp]
            [harja.domain.vesivaylat.komponentin-tilamuutos :as komp-tila]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.urakka :as urakka]
            [harja.domain.sopimus :as sop]
            [harja.domain.vesivaylat.kommentti :as kommentti]

            [harja.id :refer [id-olemassa?]]
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
            [reagent.core :as r]
            [harja.tyokalut.tuck :as tt]
            [clojure.set :as set]
            [harja.ui.modal :as modal])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def alustettu-toimenpiteen-hinnoittelu
  {::to/id nil
   ::h/hinnat nil
   ::h/tyot []})

(def alustettu-toimenpide {})

(def alustettu-hintaryhman-hinnoittelu
  {::h/id nil
   ::h/hinnat nil})

(defonce tila
  (atom {:valinnat {:urakka-id nil
                    :sopimus-id nil
                    :aikavali [nil nil]
                    :vaylatyyppi nil
                    :vaylanro nil
                    :turvalaite-id nil
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
         :korostettu-hintaryhma false
         :valittu-toimenpide nil}))

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

(def turvalaitehaku
  (reify protokollat/Haku
    (hae [_ teksti]
      (go (let [vastaus (<! (k/post! :hae-turvalaitteet-tekstilla {:hakuteksti teksti}))]
            vastaus)))))

;; Yleiset eventit
(defrecord Nakymassa? [nakymassa?])
(defrecord PaivitaValinnat [tiedot])
;; Siirto
(defrecord SiirraValitutKokonaishintaisiin [])
;; Suunnitellut työt
(defrecord TyhjennaSuunnitellutTyot [])
(defrecord HaeSuunnitellutTyot [])
(defrecord SuunnitellutTyotHaettu [vastaus])
(defrecord SuunnitellutTyotEiHaettu [])
;; Toimenpiteet
(defrecord HaeToimenpiteet [valinnat])
(defrecord ToimenpiteetHaettu [toimenpiteet])
(defrecord ToimenpiteetEiHaettu [virhe])
;; Hintaryhmät
(defrecord UudenHintaryhmanLisays? [lisays-auki?])
(defrecord UudenHintaryhmanNimeaPaivitetty [nimi])
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
(defrecord PoistaHintaryhmat [hintaryhma-idt])
(defrecord HintaryhmatPoistettu [vastaus])
(defrecord HintaryhmatEiPoistettu [])
;; Toimenpiteen hinnoittelu
(defrecord AloitaToimenpiteenHinnoittelu [toimenpide-id])
(defrecord PeruToimenpiteenHinnoittelu [])
(defrecord AsetaHintakentalleTiedot [tiedot])
(defrecord AsetaTyorivilleTiedot [tiedot])
(defrecord LisaaHinnoiteltavaTyorivi [])
(defrecord LisaaHinnoiteltavaKomponenttirivi [])
(defrecord LisaaMuuKulurivi [])
(defrecord LisaaMuuTyorivi [])
(defrecord PoistaHinnoiteltavaTyorivi [tyo])
(defrecord PoistaHinnoiteltavaHintarivi [hinta])
(defrecord TallennaToimenpiteenHinnoittelu [tiedot])
(defrecord ToimenpiteenHinnoitteluTallennettu [vastaus])
(defrecord ToimenpiteenHinnoitteluEiTallennettu [virhe])
;; Hintaryhmän hinnoittelu
(defrecord AloitaHintaryhmanHinnoittelu [hintaryhma-id])
(defrecord PeruHintaryhmanHinnoittelu [])
(defrecord AsetaHintaryhmakentalleTiedot [tiedot])
(defrecord TallennaHintaryhmanHinnoittelu [tiedot])
(defrecord HintaryhmanHinnoitteluTallennettu [vastaus])
(defrecord HintaryhmanHinnoitteluEiTallennettu [virhe])
(defrecord MuutaHintaryhmanLaskutuslupaa [id tila pvm paivitys])
(defrecord LaskutuslupaTallennettu [tulos id paivitys])
(defrecord LaskutuslupaEiTallennettu [virhe])
;; Kartta
(defrecord KorostaHintaryhmaKartalla [hintaryhma])
(defrecord PoistaHintaryhmanKorostus [])

(defrecord AvaaLomakkeelle [toimenpide])
(defrecord ToimenpidettaMuokattu [toimenpide])
(defrecord TallennaToimenpide [toimenpide])
(defrecord ToimenpideTallennettu [vastaus])
(defrecord ToimenpideEiTallennettu [virhe])

(defn- hintakentta
  [hinta]
  (merge
    {::hinta/summa (cond
                     (or (nil? (::hinta/ryhma hinta))
                         (#{:muu} (::hinta/ryhma hinta)))
                     0

                     (#{:komponentti :tyo} (::hinta/ryhma hinta))
                     nil)
     ::hinta/yleiskustannuslisa 0
     ::hinta/otsikko ""}
    hinta))

(defn- tyokentta
  [tyo]
  (merge
    {::tyo/maara 0}
    tyo))

(defn- poista-hintarivi-toimenpiteelta* [id id-avain tyot-tai-hinnat app]
  (let [rivit (get-in app [:hinnoittele-toimenpide tyot-tai-hinnat])
        paivitetyt
        (if-not (id-olemassa? id)
          (filterv #(not= (id-avain %) id) rivit)

          (mapv #(if (= (id-avain %) id)
                   (assoc % ::m/poistettu? true)
                   %)
                rivit))]
    (assoc-in app [:hinnoittele-toimenpide tyot-tai-hinnat] paivitetyt)))

(defn poista-tyorivi-toimenpiteelta [id app]
  (poista-hintarivi-toimenpiteelta* id ::tyo/id ::h/tyot app))

(defn poista-hintarivi-toimenpiteelta [id app]
  (poista-hintarivi-toimenpiteelta* id ::hinta/id ::h/hinnat app))

(defn- lisaa-hintarivi-toimenpiteelle* [id-avain tyot-tai-hinnat kentta-fn app]
  (let [jutut (get-in app [:hinnoittele-toimenpide tyot-tai-hinnat])
        idt (map id-avain jutut)
        seuraava-vapaa-id (dec (apply min (conj idt 0)))
        paivitetyt (conj jutut (kentta-fn seuraava-vapaa-id))]
    (assoc-in app [:hinnoittele-toimenpide tyot-tai-hinnat] paivitetyt)))

(defn lisaa-tyorivi-toimenpiteelle
  ([app] (lisaa-tyorivi-toimenpiteelle {} app))
  ([tyo app]
   (lisaa-hintarivi-toimenpiteelle*
     ::tyo/id
     ::h/tyot
     (fn [id]
       (tyokentta
         (merge {::tyo/id id} tyo)))
     app)))

(defn lisaa-hintarivi-toimenpiteelle
  ([app] (lisaa-hintarivi-toimenpiteelle {} app))
  ([hinta app]
   (lisaa-hintarivi-toimenpiteelle*
     ::hinta/id
     ::h/hinnat
     (fn [id]
       (hintakentta
         (merge {::hinta/id id} hinta)))
     app)))

(defn hintaryhma-korostettu? [hintaryhma {:keys [korostettu-hintaryhma]}]
  (boolean
    (when-not (false? korostettu-hintaryhma)
      (= (::h/id hintaryhma) korostettu-hintaryhma))))

(def kokonaishintaisista-siirretyt-hintaryhma
  {::h/nimi "Kokonaishintaisista siirretyt, valitse tilaus."
   ::h/id -1})

(def reimarin-lisatyot-hintaryhma
  {::h/nimi "Reimarissa lisätyöksi merkityt, valitse tilaus."
   ::h/id -2})

(defn kokonaishintaisista-siirretyt-hintaryhma? [hintaryhma]
  (= (::h/id hintaryhma) (::h/id kokonaishintaisista-siirretyt-hintaryhma)))

(defn reimarin-lisatyot-hintaryhma? [hintaryhma]
  (= (::h/id hintaryhma) (::h/id reimarin-lisatyot-hintaryhma)))

(defn valiaikainen-hintaryhma? [hintaryhma]
  (or (kokonaishintaisista-siirretyt-hintaryhma? hintaryhma)
      (reimarin-lisatyot-hintaryhma? hintaryhma)))

(defn hintaryhmattomat-toimenpiteet-valiaikaisiin-ryhmiin [toimenpiteet]
  (for [to toimenpiteet]
    (assoc to ::to/hintaryhma-id (or (::to/hintaryhma-id to)
                                     (when (::to/reimari-lisatyo? to) (::h/id reimarin-lisatyot-hintaryhma))
                                     (::h/id kokonaishintaisista-siirretyt-hintaryhma)))))

(defn poista-hintaryhmien-korostus [app]
  (assoc app :korostettu-hintaryhma false))

;; Toimenpiteen hinnoittelun yhteydessä tarjottavat vakiokentät (vectori, koska järjestys tärkeä)
(def vakiohinnat ["Yleiset materiaalit" "Matkakulut" "Muut kulut"])

(defn vakiohintakentta? [otsikko]
  (boolean ((set vakiohinnat) otsikko)))

(defn- toimenpiteen-hintakentat [hinnat]
  (vec (concat
         ;; Vakiohintakentät näytetään aina riippumatta siitä onko niille annettu hintaa
         (map-indexed (fn [index otsikko]
                        (let [olemassa-oleva-hinta (hinta/hinta-otsikolla hinnat otsikko)]
                          (hintakentta
                            (merge
                              {::hinta/id (dec (- index))
                               ::hinta/otsikko otsikko
                               ::hinta/ryhma :muu}
                              olemassa-oleva-hinta))))
                      vakiohinnat)
         ;; Loput kentät ovat käyttäjän itse lisäämiä
         (map
           hintakentta
           (remove #((set vakiohinnat) (::hinta/otsikko %))
                   hinnat)))))

;; Hintaryhmän hinta tallennetaan aina tällä hardkoodatulla nimellä
(def hintaryhman-hintakentta-otsikko "Ryhmähinta")

(defn muut-hinnat [app]
  (filter
    #(and (= (::hinta/ryhma %) :muu) (not (::m/poistettu? %)))
    (get-in app [:hinnoittele-toimenpide ::h/hinnat])))

(defn muut-tyot [app]
  (filter
    #(and (= (::hinta/ryhma %) :tyo) (not (::m/poistettu? %)))
    (get-in app [:hinnoittele-toimenpide ::h/hinnat])))

(defn komponenttien-hinnat [app]
  (filter
    #(and (= (::hinta/ryhma %) :komponentti) (not (::m/poistettu? %)))
    (get-in app [:hinnoittele-toimenpide ::h/hinnat])))

(defn ainoa-otsikon-vakiokentta? [hinnat otsikko]
  (and
    (vakiohintakentta? otsikko)
    (-> (group-by ::hinta/otsikko hinnat)
       (get otsikko)
       count
       (= 1))))

(defn- hintaryhman-hintakentat [hinnat]
  (let [ryhmahinta (hinta/hinta-otsikolla hinnat hintaryhman-hintakentta-otsikko)]
    ;; Luodaan ryhmähinnalle hintakenttä olemassa olevan ryhmähinnan perusteella.
    ;; Jos ei ole aiempaa ryhmähintaa, luo uuden hintakentän ilman id:tä.
    [(hintakentta
       (merge
         ryhmahinta
         {::hinta/otsikko hintaryhman-hintakentta-otsikko}))]))

(defn hinnoittelun-voi-tallentaa? [app]
  (let [tyot (get-in app [:hinnoittele-toimenpide ::h/tyot])
        hinnat (get-in app [:hinnoittele-toimenpide ::h/hinnat])
        komponenttien-hinnat (filter #(= :komponentti (::hinta/ryhma %)) hinnat)
        muut-tyot (filter #(= :tyo (::hinta/ryhma %)) hinnat)
        muut (filter #(= :muu (::hinta/ryhma %)) hinnat)
        hintojen-otsikot (map (juxt ::hinta/otsikko ::hinta/yksikko) (remove ::m/poistettu? hinnat))]
    (and (every? #(and (::tyo/toimenpidekoodi-id %)
                       (::tyo/maara %))
                 tyot)
         (every? #(and (not-empty (::hinta/otsikko %))
                       (::hinta/maara %)
                       (::hinta/yksikkohinta %)
                       (::hinta/yksikko %))
                 muut-tyot)
         (every? #(and (not-empty (::hinta/otsikko %))
                       (::hinta/maara %)
                       (::hinta/yksikkohinta %)
                       (::hinta/yksikko %))
                 komponenttien-hinnat)
         (every? #(and (not-empty (::hinta/otsikko %))
                       (::hinta/summa %))
                 muut)
         (or (empty? hintojen-otsikot)
             (apply distinct? hintojen-otsikot)))))

(defn hinnoiteltava-toimenpide [app]
  (some
    #(when (= (get-in app [:hinnoittele-toimenpide ::to/id])
              (::to/id %))
       %)
    (:toimenpiteet app)))

(defn tallennusparametrit [toimenpide]
  (-> toimenpide
      (assoc ::to/sopimus-id (get-in toimenpide [::to/sopimus ::sop/id])
             ::to/urakka-id (:id @nav/valittu-urakka)
             ::to/hintatyyppi :yksikkohintainen)
      (dissoc ::to/sopimus
              ::to/urakka
              ::to/turvalaite
              ::to/liitteet
              ::to/vikakorjauksia?
              ::to/komponentit
              ::to/pvm
              ::to/oma-hinnoittelu
              ::to/hintaryhma-id
              :lihavoi
              :valittu?)
      (set/rename-keys {::to/tyolaji ::to/reimari-tyolaji
                        ::to/tyoluokka ::to/reimari-tyoluokka
                        ::to/toimenpide ::to/reimari-toimenpidetyyppi})
      ;; Samalla nimellä voi palautua monta koodia.
      ;; On epäselvää, onko esim eri "Poisto" toimenpidetyypeillä mitään eroa,
      ;; jos on, se ei ainakaan näy käyttöliittymässä.
      ;; Voitaneen siis toistaiseksi tehdä vaan naivi ratkaisu, missä valitaan "satunnaisesti"
      ;; koodi.
      (update ::to/reimari-tyolaji to/reimari-tyolaji-avain->koodi)
      (update ::to/reimari-tyoluokka (comp first to/reimari-tyoluokka-avain->koodi))
      (update ::to/reimari-toimenpidetyyppi (comp first to/reimari-toimenpidetyyppi-avain->koodi))))

(defn haetut-toimenpiteet [vastaus]
  (-> vastaus
      jaettu/korosta-harjassa-luodut
      hintaryhmattomat-toimenpiteet-valiaikaisiin-ryhmiin
      jaettu/toimenpiteet-aikajarjestyksessa))

(extend-protocol tuck/Event

  Nakymassa?
  (process-event [{nakymassa? :nakymassa?} app]
    (assoc app :nakymassa? nakymassa?
               :karttataso-nakyvissa? nakymassa?))

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

  TyhjennaSuunnitellutTyot
  (process-event [_ app]
    (assoc app :suunnitellut-tyot nil))

  HaeSuunnitellutTyot
  (process-event [_ app]
    (let [urakka-id (get-in app [:valinnat :urakka-id])]
      (if (and (not (:suunniteltujen-toiden-haku-kaynnissa? app)) (some? urakka-id))
        (do (tuck-tyokalut/post! :yksikkohintaiset-tyot
                                 {:urakka urakka-id}
                                 {:onnistui ->SuunnitellutTyotHaettu
                                         :epaonnistui ->SuunnitellutTyotEiHaettu})
            (assoc app :suunniteltujen-toiden-haku-kaynnissa? true))
        app)))

  SuunnitellutTyotHaettu
  (process-event [{vastaus :vastaus} app]
    (assoc app :suunnitellut-tyot (remove (comp nil? :yksikkohinta) vastaus)
               :suunniteltujen-toiden-haku-kaynnissa? false))

  SuunnitellutTyotEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Suunniteltujen töiden haku epäonnistui!" :danger)
    (assoc app :suunniteltujen-toiden-haku-kaynnissa? false))

  HaeToimenpiteet
  (process-event [{valinnat :valinnat} app]
    (if (and (not (:toimenpiteiden-haku-kaynnissa? app))
             (some? (:urakka-id valinnat)))
      (do (tuck-tyokalut/post! :hae-yksikkohintaiset-toimenpiteet
                               (jaettu/toimenpiteiden-hakukyselyn-argumentit valinnat)
                               {:onnistui ->ToimenpiteetHaettu
                                       :epaonnistui ->ToimenpiteetEiHaettu})
          (assoc app :toimenpiteiden-haku-kaynnissa? true))
      app))

  ToimenpiteetHaettu
  (process-event [{toimenpiteet :toimenpiteet} app]
    (let [turvalaitteet-kartalle (tuck/send-async! jaettu/->HaeToimenpiteidenTurvalaitteetKartalle)]
      (go (turvalaitteet-kartalle toimenpiteet))
      (assoc app :toimenpiteet (haetut-toimenpiteet toimenpiteet)
                 :toimenpiteiden-haku-kaynnissa? false)))

  ToimenpiteetEiHaettu
  (process-event [_ app]
    (viesti/nayta! "Toimenpiteiden haku epäonnistui!" :danger)
    (assoc app :toimenpiteiden-haku-kaynnissa? false))

  UudenHintaryhmanLisays?
  (process-event [{lisays-auki? :lisays-auki?} app]
    (assoc app :uuden-hintaryhman-lisays? lisays-auki?))

  UudenHintaryhmanNimeaPaivitetty
  (process-event [{nimi :nimi} app]
    (assoc app :uusi-hintaryhma nimi))

  LuoHintaryhma
  (process-event [{nimi :nimi} app]
    (if-not (:hintaryhman-tallennus-kaynnissa? app)
      (do (tuck-tyokalut/post! :luo-hinnoittelu
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
      (do (tuck-tyokalut/post! :hae-hintaryhmat
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
      (do (tuck-tyokalut/post! :liita-toimenpiteet-hinnoitteluun
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

  PoistaHintaryhmat
  (process-event [{hintaryhma-idt :hintaryhma-idt} app]
    (if-not (:hintaryhmien-poisto-kaynnissa? app)
      (do (tuck-tyokalut/post! :poista-tyhjat-hinnoittelut
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

  AloitaToimenpiteenHinnoittelu
  (process-event [{toimenpide-id :toimenpide-id} app]
    (let [hinnoiteltava-toimenpide (to/toimenpide-idlla (:toimenpiteet app) toimenpide-id)
          toimenpiteen-oma-hinnoittelu (::to/oma-hinnoittelu hinnoiteltava-toimenpide)
          hinnat (or (::h/hinnat toimenpiteen-oma-hinnoittelu) [])
          tyot (or (::h/tyot toimenpiteen-oma-hinnoittelu) [])]
      (assoc app :hinnoittele-toimenpide
                 {::to/id toimenpide-id
                  ::to/pvm (::to/pvm hinnoiteltava-toimenpide)
                  ::h/hinnat (toimenpiteen-hintakentat hinnat)
                  ::h/tyot tyot})))

  PeruToimenpiteenHinnoittelu
  (process-event [_ app]
    (assoc app :toimenpiteen-hinnoittelun-tallennus-kaynnissa? false
               :hinnoittele-toimenpide alustettu-toimenpiteen-hinnoittelu))

  AsetaHintakentalleTiedot
  (process-event [{tiedot :tiedot} app]
    (assoc-in app [:hinnoittele-toimenpide ::h/hinnat]
              (hinta/paivita-hintajoukon-hinnan-tiedot-idlla (get-in app [:hinnoittele-toimenpide
                                                                          ::h/hinnat]) tiedot)))

  AsetaTyorivilleTiedot
  (process-event [{tiedot :tiedot} app]
    (assoc-in app [:hinnoittele-toimenpide ::h/tyot]
              (tyo/paivita-tyon-tiedot-idlla (get-in app [:hinnoittele-toimenpide ::h/tyot])
                                             tiedot)))

  LisaaHinnoiteltavaTyorivi
  (process-event [_ app]
    (lisaa-tyorivi-toimenpiteelle app))

  LisaaHinnoiteltavaKomponenttirivi
  (process-event [_ app]
    (lisaa-hintarivi-toimenpiteelle
      {::hinta/ryhma :komponentti}
      app))

  LisaaMuuKulurivi
  (process-event [_ app]
    (lisaa-hintarivi-toimenpiteelle
      {::hinta/ryhma :muu}
      app))

  LisaaMuuTyorivi
  (process-event [_ app]
    (lisaa-hintarivi-toimenpiteelle
      {::hinta/ryhma :tyo}
      app))

  PoistaHinnoiteltavaTyorivi
  (process-event [{tyo :tyo} app]
    (poista-tyorivi-toimenpiteelta (::tyo/id tyo) app))

  PoistaHinnoiteltavaHintarivi
  (process-event [{hinta :hinta} app]
    (poista-hintarivi-toimenpiteelta (::hinta/id hinta) app))

  TallennaToimenpiteenHinnoittelu
  (process-event [{tiedot :tiedot} app]
    (if-not (:toimenpiteen-hinnoittelun-tallennus-kaynnissa? app)
      (do (tuck-tyokalut/post!
            :tallenna-vv-toimenpiteen-hinta
            {::to/urakka-id (get-in app [:valinnat :urakka-id])
             ::to/id (get-in app [:hinnoittele-toimenpide ::to/id])
             ::h/tallennettavat-hinnat (get-in app [:hinnoittele-toimenpide ::h/hinnat])
             ::h/tallennettavat-tyot (get-in app [:hinnoittele-toimenpide ::h/tyot])}
            {:onnistui ->ToimenpiteenHinnoitteluTallennettu
             :epaonnistui ->ToimenpiteenHinnoitteluEiTallennettu})
          (assoc app :toimenpiteen-hinnoittelun-tallennus-kaynnissa? true))
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

  AloitaHintaryhmanHinnoittelu
  (process-event [{hintaryhma-id :hintaryhma-id} app]
    (let [hinnoiteltava-hintaryhma (h/hinnoittelu-idlla (:hintaryhmat app) hintaryhma-id)
          hinnat (::h/hinnat hinnoiteltava-hintaryhma)]
      (assoc app :hinnoittele-hintaryhma
                 {::h/id hintaryhma-id
                  ::h/hinnat (hintaryhman-hintakentat hinnat)})))

  PeruHintaryhmanHinnoittelu
  (process-event [_ app]
    (assoc app :hintaryhman-hinnoittelun-tallennus-kaynnissa? false
               :hinnoittele-hintaryhma alustettu-hintaryhman-hinnoittelu))

  AsetaHintaryhmakentalleTiedot
  (process-event [{tiedot :tiedot} app]
    (assoc-in app [:hinnoittele-hintaryhma ::h/hinnat]
              (hinta/paivita-hintajoukon-hinnan-tiedot-otsikolla (get-in app [:hinnoittele-hintaryhma
                                                                              ::h/hinnat]) tiedot)))

  TallennaHintaryhmanHinnoittelu
  (process-event [{tiedot :tiedot} app]
    (if-not (:hintaryhman-hinnoittelun-tallennus-kaynnissa? app)
      (do (tuck-tyokalut/post! :tallenna-hintaryhmalle-hinta
                               {::ur/id (get-in app [:valinnat :urakka-id])
                                ::h/id (get-in app [:hinnoittele-hintaryhma ::h/id])
                                ::h/tallennettavat-hinnat (mapv
                                                            (fn [hinta]
                                                              (merge
                                                                (when-let [id (::hinta/id hinta)]
                                                                  {::hinta/id id})
                                                                {::hinta/otsikko (::hinta/otsikko hinta)
                                                                 ::hinta/summa (::hinta/summa hinta)
                                                                 ::hinta/ryhma :muu
                                                                 ::hinta/yleiskustannuslisa (::hinta/yleiskustannuslisa hinta)}))
                                                            (get-in app [:hinnoittele-hintaryhma ::h/hinnat]))}
                               {:onnistui ->HintaryhmanHinnoitteluTallennettu
                                :epaonnistui ->HintaryhmanHinnoitteluEiTallennettu})
          (assoc app :hintaryhman-hinnoittelun-tallennus-kaynnissa? true))
      app))

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

  MuutaHintaryhmanLaskutuslupaa
  (process-event [{id :id tila :tila pvm :pvm paivitys :paivitys} app]
    ;; Paivitys on tuck-event, joka laukaistaan, kun laskutuslupa on onnistuneesti tallennettu
    (if-not (:hintaryhman-laskutusluvan-tallennus-kaynnissa? app)
      (-> app
         (tuck-tyokalut/post!
           :tallenna-hinnoittelun-kommentti
           {::h/urakka-id (get-in app [:valinnat :urakka-id])
            ::kommentti/kommentti nil
            ::kommentti/tila tila
            ::kommentti/laskutus-pvm pvm
            ::h/id id}
           {:onnistui ->LaskutuslupaTallennettu
            :onnistui-parametrit [id paivitys]
            :epaonnistui ->LaskutuslupaEiTallennettu})
         (assoc :hintaryhman-laskutusluvan-tallennus-kaynnissa? true))

      app))

  LaskutuslupaTallennettu
  (process-event [{id :id p :paivitys} app]
    ;; Paivitys on tuck-event, joka laukaistaan, kun laskutuslupa on onnistuneesti tallennettu
    ;; Käytännössä HaeHintaryhmat tai HaeToimenpiteet
    ;; Kysely ei voi suoraan palauttaa haluttuja tietoja, koska samaa tallennusta käytetään
    ;; toimenpiteille ja hintaryhmille. Parametrisoimalla siitä olisi selvitty, mutta aikapaine
    ;; painoi päälle
    (when (modal/nakyvissa?) (modal/piilota!))
    (tuck/action!
      (fn [e!]
        (e! p)))
    (-> app
        (assoc :hintaryhman-laskutusluvan-tallennus-kaynnissa? false)
        (update :hintaryhmat
                (fn [ht]
                  (map
                    (fn [h]
                      (if (= id (::h/id h))
                        (update h ::h/laskutuslupa? not)
                        h))
                    ht)))))

  LaskutuslupaEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Laskutusluvan muuttaminen epäonnistui!" :danger)
    (assoc app :hintaryhman-laskutusluvan-tallennus-kaynnissa? false))

  KorostaHintaryhmaKartalla
  (process-event [{hintaryhma :hintaryhma} {:keys [toimenpiteet] :as app}]
    (let [korostettavat-turvalaitteet (->>
                                        toimenpiteet
                                        (filter #(= (::to/hintaryhma-id %) (::h/id hintaryhma)))
                                        (map (comp ::tu/turvalaitenro ::to/turvalaite))
                                        (into #{}))]
      (-> (jaettu/korosta-kartalla korostettavat-turvalaitteet app)
          (assoc :korostettu-hintaryhma (::h/id hintaryhma)))))

  PoistaHintaryhmanKorostus
  (process-event [_ app]
    (->> app
         (poista-hintaryhmien-korostus)
         (jaettu/korosta-kartalla nil)))

  AvaaLomakkeelle
  (process-event [{:keys [toimenpide]} app]
    (assoc app :valittu-toimenpide toimenpide))

  ToimenpidettaMuokattu
  (process-event [{:keys [toimenpide]} app]
    (assoc app :valittu-toimenpide toimenpide))

  TallennaToimenpide
  (process-event [{:keys [toimenpide]} {:keys [tallennus-kaynnissa?] :as app}]
    (if-not tallennus-kaynnissa?
      (-> app
          (tuck-tyokalut/post! :tallenna-toimenpide
                               {:tallennettava (tallennusparametrit toimenpide)
                                :hakuehdot (set/rename-keys (:valinnat app) {:urakka-id ::to/urakka-id})}
                               {:onnistui ->ToimenpideTallennettu
                                :epaonnistui ->ToimenpideEiTallennettu})
          (assoc :tallennus-kaynnissa? true))

      app))

  ToimenpideTallennettu
  (process-event [{:keys [vastaus]} app]
    (-> app
        (assoc :tallennus-kaynnissa? false
               :valittu-toimenpide nil
               :toimenpiteet (haetut-toimenpiteet vastaus))))

  ToimenpideEiTallennettu
  (process-event [_ app]
    (viesti/nayta! "Virhe toimenpiteen tallennuksessa" :danger)
    (-> app
        (assoc :tallennus-kaynnissa? false))))
