(ns harja.domain.vesivaylat.toimenpide
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]
            [clojure.set :as set]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.organisaatio :as o]
            [harja.domain.sopimus :as sopimus]
            [harja.domain.urakka :as urakka]
            [harja.domain.vesivaylat.vikailmoitus :as vv-vikailmoitus]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.kiintio :as kiintio]
            [harja.domain.vesivaylat.hinta :as vv-hinta]
            [harja.domain.vesivaylat.urakoitsija :as urakoitsija]
            [harja.domain.vesivaylat.turvalaite :as vv-turvalaite]
            [harja.domain.vesivaylat.turvalaitekomponentti :as tkomp]
            [harja.domain.vesivaylat.alus :as vv-alus]
            [harja.domain.sopimus :as sopimus]
            [harja.domain.liite :as liite]
            [harja.domain.vesivaylat.sopimus :as reimari-sopimus]
            [harja.domain.vesivaylat.vayla :as vv-vayla]
            [specql.rel :as rel]
    #?@(:clj [
            [harja.kyselyt.specql-db :refer [define-tables]]
            ])
            [harja.pvm :as pvm])
  #?(:cljs
     (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["toimenpidehaun_komponentti" :harja.domain.vesivaylat.komponentti/toimenpidehaun-komponentti]
  ["toimenpidehaun_vika" :harja.domain.vesivaylat.vika/toimenpidehaun-vika]
  ["reimari_toimenpide_liite" ::toimenpide<->liite
   harja.domain.muokkaustiedot/poistettu?-sarake
   {::toimenpiteet (specql.rel/has-one
                     ::toimenpide-id
                     :harja.domain.toimenpide/toimenpide
                     :harja.domain.toimenpide/id)
    ::liitteet (specql.rel/has-one
                 ::liite-id
                 :harja.domain.liite/liite
                 :harja.domain.liite/id)}]
  ["reimari_toimenpide" ::reimari-toimenpide
   {"muokattu" ::m/muokattu
    "muokkaaja" ::m/muokkaaja-id
    "luotu" ::m/luotu
    "luoja" ::m/luoja-id
    "poistettu" ::m/poistettu?
    "poistaja" ::m/poistaja-id
    "reimari-lisatyo" ::reimari-lisatyo?
    ::hintatyyppi (specql.transform/transform (specql.transform/to-keyword))
    ::vikailmoitukset (specql.rel/has-many ::id ::vv-vikailmoitus/vikailmoitus ::vv-vikailmoitus/toimenpide-id)
    ::urakoitsija (specql.rel/has-one ::urakoitsija-id ::o/organisaatio ::o/id)
    ::urakka (specql.rel/has-one ::urakka-id ::urakka/urakka ::urakka/id)
    ::turvalaite (specql.rel/has-one ::turvalaitenro ::vv-turvalaite/turvalaite ::vv-turvalaite/turvalaitenro)
    ::sopimus (specql.rel/has-one ::sopimus-id ::sopimus/sopimus ::sopimus/id)
    ::vayla (specql.rel/has-one ::vaylanro ::vv-vayla/vayla ::vv-vayla/vaylanro)
    ::kiintio (specql.rel/has-one ::kiintio-id ::kiintio/kiintio ::kiintio/id)
    ::hinnoittelu-linkit (specql.rel/has-many
                           ::id
                           ::h/hinnoittelu<->toimenpide
                           ::h/toimenpide-id)
    ::liite-linkit (specql.rel/has-many
                     ::id
                     ::toimenpide<->liite
                     ::toimenpide-id)}])

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
   "1022541804" :vesiliikennemerkit})

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
    ;; Formatoidaan sinne päin
    (some-> tyyppi name str/capitalize)))

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

(defn toimenpide-komponentit-fmt [komponentti]
  (let [ktyyppi (::tkomp/komponenttityyppi komponentti)]
    (str (:harja.domain.vesivaylat.komponenttityyppi/luokan-nimi ktyyppi "<ei luokkaa>") ": " (:harja.domain.vesivaylat.komponenttityyppi/nimi ktyyppi "<ei nimeä>"))))

(def
  ^{:doc "Reimarin toimenpiteen tilat"}
  reimari-tilat
  {"1022541202" :suoritettu
   "1022541201" :suunniteltu
   "1022541203" :peruttu})

(defn reimari-tyolaji-avain->koodi [avain]
  (first (filter #(= (get reimari-tyolajit %) avain)
                 (keys reimari-tyolajit))))

(defn jarjesta-reimari-tyolajit [tyolajit]
  (sort-by reimari-tyolaji-fmt tyolajit))

(defn reimari-tyoluokka-avain->koodi [avain]
  (set (filter #(= (get reimari-tyoluokat %) avain)
               (keys reimari-tyoluokat))))

(defn jarjesta-reimari-tyoluokat [tyoluokat]
  (sort-by reimari-tyoluokka-fmt tyoluokat))

(defn reimari-toimenpidetyyppi-avain->koodi [avain]
  (set (filter #(= (get reimari-toimenpidetyypit %) avain)
               (keys reimari-toimenpidetyypit))))

(defn reimari-lisatyo-fmt [lisatyo?]
  (when lisatyo? "Kyllä"))

(defn hintatyyppi-fmt [hintatyyppi]
  (if (keyword? hintatyyppi)
      (name hintatyyppi)
      ""))

(defn jarjesta-reimari-toimenpidetyypit [toimenpidetyypit]
  (sort-by reimari-toimenpidetyyppi-fmt toimenpidetyypit))

(s/def ::reimari-turvalaite (s/keys :req [::vv-turvalaite/r-nro ::vv-turvalaite/r-nimi ::vv-turvalaite/r-ryhma]))
(s/def ::reimari-alus (s/keys :req [:harja.domain.vesivaylat.alus/r-tunnus :harja.domain.vesivaylat.alus/r-nimi]))

;; Harjassa työlaji/-luokka/toimenpide esitetään tietyllä avaimella
(s/def ::tyolaji (s/nilable (set (vals reimari-tyolajit))))
(s/def ::tyoluokka (s/nilable (set (vals reimari-tyoluokat))))
(s/def ::toimenpide (s/nilable (set (vals reimari-toimenpidetyypit))))
;; Reimarin työlaji/-luokka/toimenpide ovat tiettyjä string-koodiarvoja
(s/def ::reimari-tyolaji (s/nilable (set (keys reimari-tyolajit))))
(s/def ::reimari-tyoluokka (s/nilable (set (keys reimari-tyoluokat))))
(s/def ::reimari-tyoluokat (s/and set? (s/every ::reimari-tyoluokka)))
(s/def ::reimari-toimenpidetyyppi (s/nilable (set (keys reimari-toimenpidetyypit))))
(s/def ::reimari-toimenpidetyypit (s/and set? (s/every ::reimari-toimenpidetyyppi)))

(s/def ::komponentit (s/every ::tkomp/turvalaitekomponentti))
(s/def ::vayla (s/keys :opt [::vv-vayla/tyyppi
                             ::vv-vayla/vaylanro
                             ::vv-vayla/nimi]))
(s/def ::pvm inst?)
(s/def ::turvalaite (s/nilable (s/keys :opt [::vv-turvalaite/nimi
                                             ::vv-turvalaite/nro
                                             ::vv-turvalaite/ryhma])))
(s/def ::oma-hinnoittelu ::h/hinnoittelu)
(s/def ::hintaryhma-id ::h/id)
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
    ::reimari-vayla
    ::reimari-viat
    ::reimari-henkilo-lkm})

(def metatiedot m/muokkauskentat)

(def viittaus-idt
  #{::urakoitsija-id
    ::sopimus-id
    ::urakka-id
    ::turvalaitenro
    ::vaylanro
    ::m/muokkaaja-id
    ::m/luoja-id
    ::m/poistaja-id
    ::kiintio-id})

(def hinnoittelu
  #{[::hinnoittelu-linkit (set/union
                            m/muokkauskentat
                            h/toimenpiteen-hinnoittelut)]})

(def liitteet
  #{[::liite-linkit #{::toimenpide-id ::liite-id ::m/poistettu?}]})

(def vikailmoitus #{[::vikailmoitukset vv-vikailmoitus/perustiedot]})
(def urakoitsija #{[::urakoitsija o/urakoitsijan-perustiedot]})
(def sopimus #{[::sopimus sopimus/perustiedot]})
(def turvalaite #{[::turvalaite vv-turvalaite/perustiedot]})
(def vayla #{[::vayla vv-vayla/perustiedot]})
(def urakka #{[::urakka #{}]})
(def kiintio #{[::kiintio kiintio/perustiedot]})

(def viittaukset
  (clojure.set/union
    vikailmoitus
    urakoitsija
    sopimus
    turvalaite
    urakka
    vayla
    kiintio
    vikailmoitus))

(def perustiedot
  #{::id
    ::lisatieto
    ::harjassa-luotu
    ::reimari-lisatyo?
    ::suoritettu
    ::hintatyyppi
    ::reimari-henkilo-lkm})

(defn toimenpide-idlla [toimenpiteet id]
  (first (filter #(= (::id %) id) toimenpiteet)))

(defn toimenpiteet-tyolajilla [toimenpiteet tyolaji]
  (filter #(= (::tyolaji %) tyolaji) toimenpiteet))

(defn toimenpiteet-hintaryhmalla [toimenpiteet hintaryhma-id]
  (filter #(= (::hintaryhma-id %) hintaryhma-id) toimenpiteet))

(defn toimenpiteet-kiintiolla [toimenpiteet kiintio-id]
  (filter #(= (get-in % [::kiintio ::kiintio/id]) kiintio-id) toimenpiteet))

(defn toimenpiteet-vaylalla [toimenpiteet vaylanro]
  (filter #(= (get-in % [::vayla ::vv-vayla/vaylanro]) vaylanro) toimenpiteet))

(defn toimenpiteiden-vaylat [toimenpiteet]
  (distinct (map #(::vayla %) toimenpiteet)))

(defn ilman-toimenpiteita [toimenpiteet poistettavat-idt]
  (filter (comp not poistettavat-idt ::id) toimenpiteet))

(defn toimenpiteella-oma-hinnoittelu? [toimenpide]
  (boolean (or (not (empty? (get-in toimenpide [::oma-hinnoittelu ::h/hinnat])))
               (not (empty? (get-in toimenpide [::oma-hinnoittelu ::h/tyot]))))))

(defn toimenpiteilla-kiintioita? [toimenpiteet]
  (not (empty? (keep ::kiintio toimenpiteet))))

(defn toimenpiteilla-hintaryhmia? [toimenpiteet]
  (not (empty? (keep ::hintaryhma-id toimenpiteet))))

(defn toimenpiteilla-omia-hinnoitteluja? [toimenpiteet]
  (not (empty? (keep ::oma-hinnoittelu toimenpiteet))))

;; Palvelut

(s/def ::hae-vesivaylien-toimenpiteet-kysely
  (s/keys
    ;; Toimenpiteen / toteuman hakuparametrit
    :req [::urakka-id]
    :opt [::sopimus-id ::vv-vayla/vaylatyyppi ::vaylanro ::turvalaitenro
          ::reimari-tyolaji ::reimari-tyoluokat ::reimari-toimenpidetyypit]
    ;; Muut hakuparametrit
    :opt-un [::alku ::loppu ::luotu-alku ::luotu-loppu
             ::vikailmoitukset? ::tyyppi ::urakoitsija-id]))

(s/def ::hae-vesivayilien-yksikkohintaiset-toimenpiteet-vastaus
  (s/coll-of (s/keys :req [::id
                           ::tyolaji
                           ::tyoluokka
                           ::toimenpide
                           ::pvm
                           ::komponentit]
                     :opt [::vikakorjauksia?
                           ::vayla
                           ::suoritettu
                           ::hintatyyppi
                           ::lisatieto
                           ::oma-hinnoittelu
                           ::hintaryhma-id
                           ::reimari-urakoitsija
                           ::turvalaite
                           ::reimari-sopimus])))

(s/def ::hae-vesivayilien-kokonaishintaiset-toimenpiteet-vastaus
  (s/coll-of (s/keys :req [::id
                           ::tyolaji
                           ::tyoluokka
                           ::toimenpide
                           ::pvm
                           ::komponentit]
                     :opt [::vikakorjauksia?
                           ::vayla
                           ::suoritettu
                           ::hintatyyppi
                           ::lisatieto
                           ::turvalaite
                           ::reimari-urakoitsija
                           ::reimari-sopimus])))

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

(s/def ::lisaa-toimenpiteelle-liite-kysely
  (s/keys :req [::id ::urakka-id ::liite-id]))

(s/def ::poista-toimenpiteen-liite-kysely
  (s/keys :req [::id ::urakka-id ::liite-id]))

(s/def ::tallennettava
  (s/keys :req [::sopimus-id ::urakka-id]
          :opt [::lisatieto
                ::id ::luoja ::luotu ::muokattu ::muokkaaja ::poistettu ::poistaja
                ::reimari-tyolaji ::reimari-tyoluokka ::reimari-toimenpidetyyppi]))

(s/def ::hakuehdot ::hae-vesivaylien-toimenpiteet-kysely)

(s/def ::tallenna-toimenpide-kysely
  (s/keys :req-un [::hakuehdot ::tallennettava]))
