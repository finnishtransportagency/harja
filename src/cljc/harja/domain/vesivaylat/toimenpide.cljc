(ns harja.domain.vesivaylat.toimenpide
  (:require [clojure.spec.alpha :as s]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.organisaatio :as o]
            [harja.domain.sopimus :as sopimus]
            [harja.domain.urakka :as urakka]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.hinta :as vv-hinta]
            [harja.domain.vesivaylat.turvalaite :as vv-turvalaite]
            [clojure.string :as str]
            [harja.domain.vesivaylat.vikailmoitus :as vv-vikailmoitus]
            [harja.domain.vesivaylat.vayla :as vv-vayla]
            [specql.rel :as rel]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            [clojure.future :refer :all]]))
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(def
  ^{:doc "Reimarin työlajit."}
  reimari-tyolajit
  {"1022541807" :tukityot
   "1022541801" :kiinteat-turvalaitteet
   "1022541802" :poijut
   "1022541803" :viitat
   "1022541805" :muut-vaylatyot
   "1022541806" :rakennus-ja-kuljetuspalvelut
   "1022541808" :muut-palvelut
   "1022541804" :vesiliikennemerkit
   "1022540501" :kiintea-turvalaite})

(defn reimari-tyolaji-avain->koodi [avain]
  (first (filter #(= (get reimari-tyolajit %) avain)
                 (keys reimari-tyolajit))))

(defn reimari-tyolaji-fmt [tyyppi]
  (case tyyppi
    :tukityot "Tukityöt"
    :kiinteat-turvalaitteet "Kiinteät turvalaitteet"
    :poijut "Poijut"
    :viitat "Viitat"
    :muut-vaylatyot "Muut väylätyöt"
    :rakennus-ja-kuljetuspalvelut "Rakennus- ja kuljetuspalvelut"
    :muut-palvelut "Muut palvelut"
    :vesiliikennemerkit "Vesiliikennemerkit"
    :kiintea-turvalaite "Kiinteä turvalaite"
    ;; Formatoidaan sinne päin
    (some-> tyyppi name str/capitalize)))

(defn jarjesta-reimari-tyolajit [tyolajit]
  (sort-by reimari-tyolaji-fmt tyolajit))

(def
  ^{:doc "Reimarin työluokat. Huom: eri koodeilla voi olla sama selite."}
  reimari-tyoluokat
  {"1022541920" :kuljetuskaluston-huolto-ja-kunnossapito
   "1022541918" :toimistotyot
   "1022541901" :valo-ja-energialaitteet
   "1022541921" :turvalaitteiden-tukikohtatyot
   "1022541906" :asennus-ja-huolto
   "1022541909" :asennus-ja-huolto
   "1022541905" :valo-ja-energialaitteet
   "1022541919" :tukikohtatyot
   "1022541915" :tarkastustyot
   "1022541922" :muut-tukikohtatyot
   "1022541904" :ymparistotyot
   "1022541908" :valo-ja-energialaitteet
   "1022541903" :rakenteet
   "1022541914" :navi
   "1022541916" :rakennuspalvelut
   "1022541925" :muut-tyot
   "1022541917" :kuljetuspalvelut
   "1022541923" :vts-tyot
   "1022541911" :rakenteet
   "1022541913" :pienehkot-vaylatyot
   "1022541912" :ymparistotyot
   "1022541902" :telematiikkalaitteet
   "1022541924" :luotsitoiminnan-palvelut
   "1022541907" :telematiikkalaitteet
   "1022541910" :telematiikkalaitteet})

(defn reimari-tyoluokka-avain->koodi [avain]
  (set (filter #(= (get reimari-tyoluokat %) avain)
               (keys reimari-tyoluokat))))

(defn reimari-tyoluokka-fmt [tyoluokka]
  (case tyoluokka
    :kuljetuskaluston-huolto-ja-kunnossapito "Kuljetuskaluston huolto ja kunnossapito"
    :toimistotyot "Toimistotyöt"
    :valo-ja-energialaitteet "Valo- ja energialaitteet"
    :turvalaitteiden-tukikohtatyot "Turvalaitteiden tukikohtatyöt"
    :asennus-ja-huolto "Asennus ja huolto"
    :tukikohtatyot "Tukikohtatyöt"
    :tarkastustyot "Tarkastustyöt"
    :muut-tukikohtatyot "Muut tukikohtatyöt"
    :ymparistotyot "Ympäristötyöt"
    :navi "Ravi"
    :rakennuspalvelut "Rakennuspalvelut"
    :muut-tyot "Muut työt"
    :kuljetuspalvelut "Kuljetuspalvelut"
    :vts-tyot "VTS-työt"
    :rakenteet "Rakenteet"
    :pienehkot-vaylatyot "Pienehköt väylätyöt"
    :telematiikkalaitteet "Telematiikkalaitteet"
    :luotsitoiminnan-palvelut "Luotsitoiminnan palvelut"
    ;; Formatoidaan sinne päin
    (some-> tyoluokka name str/capitalize)))

(defn jarjesta-reimari-tyoluokat [tyoluokat]
  (sort-by reimari-tyoluokka-fmt tyoluokat))

(def ^{:doc "Reimarin toimenpidetyypit."}
reimari-toimenpidetyypit
  {"1022542046" :alukset-ja-veneet
   "1022542040" :toimistotyot
   "1022542048" :muu-kuljetuskalusto
   "1022542001" :valo-ja-energialaitetyot
   "1022542050" :poiju-ja-viittakorjaustyot
   "1022542010" :sijaintitarkastus
   "1022542019" :sijaintitarkastus
   "1022542009" :asennus-tarkastus-ja-vaihto
   "1022542021" :siirto
   "1022542047" :autot-traktorit
   "1022542042" :T&K
   "1022542044" :kiinteistojen-yllapito-ja-huolto
   "1022542022" :vaihto
   "1022542020" :asennus
   "1022542033" :tarkastustyot
   "1022542053" :muut-tukityot
   "1022542045" :muun-kaluston-kunnossapito
   "1022542052" :muut-turvalaitetyot
   "1022542024" :ankkurointityot
   "1022542008" :raivaus-ja-ymparistonhoito
   "1022542041" :koulutus
   "1022542051" :valo-ja-energialaitetyot
   "1022542018" :asennus-tarkastus-ja-vaihto
   "1022542006" :peruskorjaus
   "1022542043" :varasto-ja-hankintatyot
   "1022542049" :ankkuripainojen-tyot
   "1022542032" :navityot
   "1022542034" :kiinteistot
   "1022542005" :tarkastus-huolto-ja-korjaus
   "1022542062" :huoltotyo
   "1022542060" :muut-palvelut
   "1022542025" :sijoittajatyot
   "1022542038" :aluskalustolla
   "1022542002" :sektorien-tarkastus-ja-saato
   "1022542039" :auto-tai-muulla-kalustolla
   "1022542061" :huoltotyo
   "1022542007" :uudisrakentaminen
   "1022542023" :poisto
   "1022542054" :VTS-tyot
   "1022542013" :vaihto
   "1022542011" :asennus
   "1022542003" :tutkamajakkatyot
   "1022542012" :siirto
   "1022542027" :tarkastus-huolto-ja-korjaus
   "1022542031" :kivien-ym-esteiden-poisto
   "1022540608" :sektorin-tarkastus
   "1022542037" :uittorakenteet
   "1022542035" :satamarakenteet
   "1022542016" :sijoittajatyot
   "1022542030" :raivaus-ja-ymparistonhoito
   "1022542058" :pelastustoimintapalvelut
   "1022542029" :uudisrakentaminen
   "1022542036" :kanavarakenteet
   "1022542028" :peruskorjaus
   "1022542004" :kaukovalvontalaitetyot
   "1022542014" :poisto
   "1022542015" :ankkurointityot
   "1022542059" :jaanmurtopalvelut
   "1022542056" :tutkintoajo
   "1022542055" :luotsiajo
   "1022542017" :kaukovalvontalaitetyot
   "1022542026" :kaukovalvontalaitetyot})

(defn reimari-toimenpidetyyppi-avain->koodi [avain]
  (set (filter #(= (get reimari-toimenpidetyypit %) avain)
               (keys reimari-toimenpidetyypit))))

(defn reimari-toimenpidetyyppi-fmt [toimenpide]
  (case toimenpide
    :alukset-ja-veneet "Alukset ja veneet"
    :toimistotyot "Toimistötyöt"
    :muu-kuljetuskalusto "Muu kuljetuskalusto"
    :valo-ja-energialaitetyot "Valo ja energialaitetyöt"
    :poiju-ja-viittakorjaustyot "Polju- ja viittakorjaustyöt"
    :sijaintitarkastus "Sijaintitarkastus"
    :asennus-tarkastus-ja-vaihto "Asennus, tarkastus ja vaihto"
    :autot-traktorit "Autot, traktorit"
    :T&K "T&K"
    :kiinteistojen-yllapito-ja-huolto "Kiinteistöjen ylläpito ja huolto"
    :asennus "Asennus"
    :tarkastustyot "Tarkastustyöt"
    :muut-tukityot "Muut tukityöt"
    :muun-kaluston-kunnossapito "Muun kaluston kunnossapito"
    :muut-turvalaitetyot "Muut turvalaitetyöt"
    :raivaus-ja-ymparistonhoito "Raivaus ja ympäristönhoito"
    :koulutus "Koulutus"
    :varasto-ja-hankintatyot "Varasto- ja hankintatyöt"
    :ankkuripainojen-tyot "Ankkuripainojen työt"
    :navityot "Navityöt"
    :kiinteistot "Kiinteistöt"
    :muut-palvelut "Muut palvelut"
    :sijoittajatyot "Sijoittajatyöt"
    :aluskalustolla "Aluskalustolla"
    :sektorien-tarkastus-ja-saato "Sektorien tarkastus ja säätö"
    :auto-tai-muulla-kalustolla "Auto tai muulla kalustolla"
    :huoltotyo "Huoltotyö"
    :uudisrakentaminen "Uudisrakentaminen"
    :VTS-tyot "VTS-työt"
    :tutkamajakkatyot "Tutkamajakkatyöt"
    :siirto "Siirto"
    :tarkastus-huolto-ja-korjaus "Tarkastus, huolto ja korjaus"
    :kivien-ym-esteiden-poisto "Kivien ym. esteiden poisto"
    :sektorin-tarkastus "Sektorien tarkastus"
    :uittorakenteet "Uittorakenteet"
    :satamarakenteet "Satamarakenteet"
    :pelastustoimintapalvelut "Pelastustoimintapalvelut"
    :kanavarakenteet "Kanavarakenteet"
    :peruskorjaus "Peruskorjaus"
    :kaukovalvontalaitetyot "Kaukovalvontalaitetyöt"
    :poisto "Poisto"
    :ankkurointityot "Ankkurityöt"
    :jaanmurtopalvelut "Jäänmurtopalvelut"
    :tutkintoajo "Tutkintoajo"
    :luotsiajo "Luotsiajo"
    ;; Formatoidaan sinne päin
    (some-> toimenpide name str/capitalize)))

(defn jarjesta-reimari-toimenpidetyypit [toimenpidetyypit]
  (sort-by reimari-toimenpidetyyppi-fmt toimenpidetyypit))

(def ^{:doc "Reimarin toimenpiteen tilat"}
reimari-tilat
  {"1022541202" :suoritettu
   "1022541201" :suunniteltu
   "1022541203" :peruttu})

(define-tables
  ["vv_toimenpide_hintatyyppi" ::toimenpide-hintatyyppi (specql.transform/transform (specql.transform/to-keyword))]
  ["reimari_toimenpide" ::reimari-toimenpide
   {"muokattu" ::m/muokattu
    "muokkaaja" ::m/muokkaaja-id
    "luotu" ::m/luotu
    "luoja" ::m/luoja-id
    "poistettu" ::m/poistettu?
    "poistaja" ::m/poistaja-id
    "lisatyo" ::lisatyo?
    ::vikailmoitukset (specql.rel/has-many ::id ::vv-vikailmoitus/vikailmoitus ::vv-vikailmoitus/toimenpide-id)
    ::urakoitsija (specql.rel/has-one ::urakoitsija-id ::o/organisaatio ::o/id)
    ::urakka (specql.rel/has-one ::urakka-id ::urakka/urakka ::urakka/id)
    ::turvalaite (specql.rel/has-one ::turvalaite-id ::vv-turvalaite/turvalaite ::vv-turvalaite/id)
    ::sopimus (specql.rel/has-one ::sopimus-id ::sopimus/sopimus ::sopimus/id)
    ::vayla (specql.rel/has-one ::vayla-id ::vv-vayla/vayla ::vv-vayla/id)
    ::hinnoittelu-linkit (specql.rel/has-many
                           ::id
                           ::h/hinnoittelu<->toimenpide
                           ::h/toimenpide-id)}])

;; Harjassa työlaji/-luokka/toimenpide esitetään tietyllä avaimella
(s/def ::tyolaji (set (vals reimari-tyolajit)))
(s/def ::tyoluokka (set (vals reimari-tyoluokat)))
(s/def ::toimenpide (set (vals reimari-toimenpidetyypit)))
;; Reimarin työlaji/-luokka/toimenpide ovat tiettyjä string-koodiarvoja
(s/def ::reimari-tyolaji (set (keys reimari-tyolajit)))
(s/def ::reimari-tyoluokka (set (keys reimari-tyoluokat)))
(s/def ::reimari-tyoluokat (s/and set? (s/every ::reimari-tyoluokka)))
(s/def ::reimari-toimenpidetyyppi (set (keys reimari-toimenpidetyypit)))
(s/def ::reimari-toimenpidetyypit (s/and set? (s/every ::reimari-toimenpidetyyppi)))

(s/def ::vayla (s/keys :opt [::vv-vayla/tyyppi
                             ::vv-vayla/id
                             ::vv-vayla/nimi]))
(s/def ::pvm inst?)
(s/def ::turvalaite (s/keys :opt [::vv-turvalaite/nimi
                                  ::vv-turvalaite/nro
                                  ::vv-turvalaite/ryhma]))
(s/def ::oma-hinnoittelu ::h/hinnoittelu)
(s/def ::hintaryhma ::h/hinnoittelu)
(s/def ::vikakorjauksia? boolean?)
(s/def ::idt (s/coll-of ::id))

(def reimari-kentat
  #{::reimari-id
    ::reimari-tyolaji
    ::reimari-tyoluokka
    ::reimari-toimenpidetyyppi
    ::reimari-tila
    ::reimari-luotu
    ::reimari-muokattu
    ::reimari-asiakas
    ::reimari-vastuuhenkilo
    ::reimari-alus
    ::reimari-urakoitsija
    ::reimari-sopimus
    ::reimari-turvalaite
    ::reimari-vayla})

(def metatiedot m/muokkauskentat)

(def viittaus-idt
  #{::toteuma-id
    ::urakoitsija-id
    ::sopimus-id
    ::urakka-id
    ::turvalaite-id
    ::vayla-id
    ::luoja
    ::luoja-id})

(def hinnoittelu
  #{[::hinnoittelu-linkit h/toimenpiteen-hinnoittelut]})

(def vikailmoitus #{[::vikailmoitukset vv-vikailmoitus/perustiedot]})
(def urakoitsija #{[::urakoitsija o/urakoitsijan-perustiedot]})
(def sopimus #{[::sopimus sopimus/perustiedot]})
(def turvalaite #{[::turvalaite vv-turvalaite/perustiedot]})
(def vayla #{[::vayla vv-vayla/perustiedot]})
(def urakka #{[::urakka #{}]})

(def viittaukset
  (clojure.set/union
    vikailmoitus
    urakoitsija
    sopimus
    turvalaite
    urakka
    vayla))

(def perustiedot
  #{::id
    ::lisatieto
    ::suoritettu
    ::lisatyo?
    ::hintatyyppi})

(defn toimenpide-idlla [toimenpiteet id]
  (first (filter #(= (::id %) id) toimenpiteet)))

(defn toimenpiteet-tyolajilla [toimenpiteet tyolaji]
  (filter #(= (::tyolaji %) tyolaji) toimenpiteet))

(defn toimenpiteet-vaylalla [toimenpiteet vayla-id]
  (filter #(= (get-in % [::vayla ::vv-vayla/id]) vayla-id) toimenpiteet))

(defn toimenpiteiden-vaylat [toimenpiteet]
  (distinct (map #(::vayla %) toimenpiteet)))

(defn toimenpiteet-hintaryhmissa [toimenpiteet]
  (let [hintaryhmilla-ryhmiteltyna
        (group-by
          (fn [h]
            (first (filter (comp ::h/hintaryhma?
                                 ::h/hinnoittelut)
                           (::hinnoittelu-linkit h))))
          toimenpiteet)]

    ;; Ilman redundanttia hintaryhmää toimenpiteen hinnoittelutiedoissa
    (into {}
          (map
            (fn [[hintaryhma toimenpiteet]]
              {hintaryhma
               (map
                 (fn [t]
                   (update t ::hinnoittelu-linkit
                           #(remove
                              (comp ::h/hintaryhma?
                                    ::h/hinnoittelut) %)))
                 toimenpiteet)})
            hintaryhmilla-ryhmiteltyna))))

;; Palvelut

(s/def ::hae-vesivaylien-toimenpiteet-kysely
  (s/keys
    ;; Toimenpiteen / toteuman hakuparametrit
    :req [::urakka-id]
    :opt [::sopimus-id ::vv-vayla/vaylatyyppi ::vayla-id
          ::reimari-tyolaji ::reimari-tyoluokat ::reimari-toimenpidetyypit]
    ;; Muut hakuparametrit
    :opt-un [::alku ::loppu ::luotu-alku ::luotu-loppu
             ::vikailmoitukset? ::tyyppi ::urakoitsija-id]))

(s/def ::hae-vesivayilien-toimenpiteet-vastaus
  (s/coll-of (s/keys :req [::id ::tyolaji ::vayla
                           ::tyoluokka ::toimenpide ::pvm
                           ::turvalaite]
                     :opt [::vikakorjauksia?])))

(s/def ::siirra-toimenpiteet-yksikkohintaisiin-kysely
  (s/keys
    :req [::urakka-id ::idt]))

(s/def ::siirra-toimenpiteet-yksikkohintaisiin-vastaus
  ::idt) ; Päivitetyt toimenpide-idt (samat kuin lähetetyt)

(s/def ::siirra-toimenpiteet-kokonaishintaisiin-kysely
  (s/keys
    :req [::urakka-id ::idt]))

(s/def ::siirra-toimenpiteet-kokonaishintaisiin-vastaus
  ::idt) ; Päivitetyt toimenpide-idt (samat kuin lähetetyt)