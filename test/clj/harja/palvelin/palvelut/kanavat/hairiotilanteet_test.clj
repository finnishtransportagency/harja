(ns harja.palvelin.palvelut.kanavat.hairiotilanteet-test
  (:require [clojure.test :refer :all]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [com.stuartsierra.component :as component]
            [harja
             [pvm :as pvm]
             [testi :refer :all]]
            [harja.palvelin.palvelut.yllapito-toteumat :refer :all]
            [harja.tyokalut.functor :refer [fmap]]
            [clojure.spec.alpha :as s]
            [harja.palvelin.palvelut.kanavat.hairiotilanteet :as hairiotilanteet]
            [harja.domain.kanavat.hairiotilanne :as hairiotilanne]
            [harja.domain.vesivaylat.materiaali :as vv-materiaali]
            [clojure.test.check.generators :as gen]
            [harja.jms-test :refer [feikki-sonja]]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.komponentit.fim-test :refer [+testi-fim+]]
            [harja.palvelin.integraatiot.integraatioloki :as integraatioloki]
            [harja.palvelin.integraatiot.sonja.sahkoposti :as sahkoposti]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.palvelut.vesivaylat.materiaalit :as vv-materiaalit]
            [namespacefy.core :as namespacefy])
  (:import (java.util UUID)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
                  (fn [_]
                    (component/start
                      (component/system-map
                        :db (tietokanta/luo-tietokanta testitietokanta)
                        :http-palvelin (testi-http-palvelin)
                        :fim (component/using
                               (fim/->FIM +testi-fim+)
                               [:db :integraatioloki])
                        :integraatioloki (component/using
                                           (integraatioloki/->Integraatioloki nil)
                                           [:db])
                        :sonja (feikki-sonja)
                        :sonja-sahkoposti (component/using
                                            (sahkoposti/luo-sahkoposti "foo@example.com"
                                                                       {:sahkoposti-sisaan-jono "email-to-harja"
                                                                        :sahkoposti-ulos-jono "harja-to-email"
                                                                        :sahkoposti-ulos-kuittausjono "harja-to-email-ack"})
                                            [:sonja :db :integraatioloki])
                        :vv-materiaalit (component/using
                                          (vv-materiaalit/->Materiaalit)
                                          [:db :http-palvelin :fim :sonja-sahkoposti])
                        :kan-hairio (component/using
                                      (hairiotilanteet/->Hairiotilanteet)
                                      [:http-palvelin :db :fim :sonja-sahkoposti])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each (compose-fixtures tietokanta-fixture jarjestelma-fixture))

(deftest hairiotilanteiden-haku
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        lisasopimus-id (hae-saimaan-kanavaurakan-lisasopimuksen-id)
        saimaan-kaikki-hairiot (ffirst (q "SELECT COUNT(*) FROM kan_hairio WHERE urakka = " urakka-id ";"))]

    (testing "Haku urakkalla"
      (let [args {::hairiotilanne/urakka-id urakka-id}
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :hae-hairiotilanteet
                                    +kayttaja-jvh+
                                    args)]

        (is (s/valid? ::hairiotilanne/hae-hairiotilanteet-kysely args))
        (is (s/valid? ::hairiotilanne/hae-hairiotilanteet-vastaus vastaus))
        (is (>= (count vastaus) saimaan-kaikki-hairiot))))

    (testing "Haku tyhjällä urakkalla ei ole validi"
      (is (not (s/valid? ::hairiotilanne/hae-hairiotilanteet-kysely
                         {::hairiotilanne/urakka-id nil}))))

    ;; Testataan filtterit: jokaisen käyttö pitäisi palauttaa pienempi setti kuin
    ;; kaikki urakan häiriöt

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-sopimus-id lisasopimus-id}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-vikaluokka :sahkotekninen_vika}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-korjauksen-tila :kesken}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-korjauksen-tila :kesken}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-odotusaika-h [60 60]}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-korjausaika-h [20 20]}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-paikallinen-kaytto? true}))
           saimaan-kaikki-hairiot))

    (is (< (count (kutsu-palvelua (:http-palvelin jarjestelma)
                                  :hae-hairiotilanteet
                                  +kayttaja-jvh+
                                  {::hairiotilanne/urakka-id urakka-id
                                   :haku-aikavali [(pvm/luo-pvm 2015 0 1)
                                                   (pvm/luo-pvm 2015 2 1)]}))
           saimaan-kaikki-hairiot))))

(deftest hairiotilanteiden-haku-ilman-oikeuksia
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        args {::hairiotilanne/urakka-id urakka-id}]

    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :hae-hairiotilanteet
                                           +kayttaja-ulle+
                                           args)))))

(defn tallennuksen-argumentit [{:keys [id urakka-id kohde-id kohdeosa-id syy naulan-kulutus amparin-kulutus]}]
  (let [naulan-kulutus (or naulan-kulutus 0)
        amparin-kulutus (or amparin-kulutus 0)
        urakka-id (or urakka-id (hae-saimaan-kanavaurakan-id))
        sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
        syy (or syy "")
        kohde-id (or kohde-id (hae-saimaan-kanavan-tikkalasaaren-sulun-kohde-id))
        kohteenosa-id kohdeosa-id]
    {::hairiotilanne/hairiotilanne (merge
                                     (when id {::hairiotilanne/id id})
                                     {::hairiotilanne/sopimus-id sopimus-id
                                      ::hairiotilanne/kohde-id kohde-id
                                      ::hairiotilanne/kohteenosa-id kohteenosa-id
                                      ::hairiotilanne/paikallinen-kaytto? true
                                      ::hairiotilanne/vikaluokka :sahkotekninen_vika
                                      ::hairiotilanne/korjaustoimenpide "Vähennetään sähköä"
                                      ::hairiotilanne/korjauksen-tila :kesken
                                      ::hairiotilanne/havaintoaika (pvm/luo-pvm 2017 11 17)
                                      ::hairiotilanne/urakka-id urakka-id
                                      ::hairiotilanne/kuittaaja-id (:id +kayttaja-jvh+)
                                      ::hairiotilanne/huviliikenne-lkm 1
                                      ::hairiotilanne/korjausaika-h 1
                                      ::hairiotilanne/syy syy
                                      ::hairiotilanne/odotusaika-h 4
                                      ::hairiotilanne/ammattiliikenne-lkm 2})
     ::vv-materiaali/materiaalikirjaukset [{::vv-materiaali/maara naulan-kulutus
                                            ::vv-materiaali/nimi "Naulat"
                                            ::vv-materiaali/urakka-id urakka-id
                                            ::vv-materiaali/pvm (pvm/nyt)
                                            ::vv-materiaali/yksikko "kpl"}
                                           {::vv-materiaali/maara amparin-kulutus
                                            ::vv-materiaali/nimi "Ämpäreitä"
                                            ::vv-materiaali/urakka-id urakka-id
                                            ::vv-materiaali/pvm (pvm/nyt)
                                            ::vv-materiaali/yksikko "kpl"}]
     ::vv-materiaali/poista-materiaalikirjauksia []
     ::hairiotilanne/hae-hairiotilanteet-kysely {::hairiotilanne/urakka-id urakka-id}}))

(deftest hairiotilanteiden-tallennus-ilman-oikeuksia
  (let [syy (str "hairiotilanteen-tallennus-testi-" (UUID/randomUUID))
        parametrit (tallennuksen-argumentit {:syy syy})]
    (defn tallennuksen-parametrit [{:keys [naulan-kulutus amparin-kulutus]}]
      (let [naulan-kulutus (or naulan-kulutus 0)
            amparin-kulutus (or amparin-kulutus 0)
            urakka-id (hae-saimaan-kanavaurakan-id)
            sopimus-id (hae-saimaan-kanavaurakan-paasopimuksen-id)
            kohde-id (hae-saimaan-kanavan-tikkalasaaren-sulun-kohde-id)]
        {::hairiotilanne/hairiotilanne {::hairiotilanne/sopimus-id sopimus-id
                                        ::hairiotilanne/kohde-id kohde-id
                                        ::hairiotilanne/paikallinen-kaytto? true
                                        ::hairiotilanne/vikaluokka :sahkotekninen_vika
                                        ::hairiotilanne/korjaustoimenpide "Vähennetään sähköä"
                                        ::hairiotilanne/korjauksen-tila :kesken
                                        ::hairiotilanne/havaintoaika (pvm/luo-pvm 2017 11 17)
                                        ::hairiotilanne/urakka-id urakka-id
                                        ::hairiotilanne/kuittaaja-id (:id +kayttaja-jvh+)
                                        ::hairiotilanne/huviliikenne-lkm 1
                                        ::hairiotilanne/korjausaika-h 1
                                        ::hairiotilanne/syy "hairiotilanteen-tallennus-testi"
                                        ::hairiotilanne/odotusaika-h 4
                                        ::hairiotilanne/ammattiliikenne-lkm 2}
         ::hairiotilanne/hae-hairiotilanteet-kysely {::hairiotilanne/urakka-id urakka-id}
         ::vv-materiaali/materiaalikirjaukset [{::vv-materiaali/maara naulan-kulutus
                                                ::vv-materiaali/nimi "Naulat"
                                                ::vv-materiaali/urakka-id urakka-id
                                                ::vv-materiaali/pvm (pvm/nyt)
                                                ::vv-materiaali/yksikko "kpl"}
                                               {::vv-materiaali/maara amparin-kulutus
                                                ::vv-materiaali/nimi "Ämpäreitä"
                                                ::vv-materiaali/urakka-id urakka-id
                                                ::vv-materiaali/pvm (pvm/nyt)
                                                ::vv-materiaali/yksikko "kpl"}]
         ::vv-materiaali/poista-materiaalikirjauksia []}))))

(deftest hairiotilanteen-materiaalin-kulutus
  (let [urakka-id (hae-saimaan-kanavaurakan-id)
        ;; pistetään häiriötilanne id atomiin, niin voidaan käsitellä samaa häiriötilannetta
        ;; materiaalin poisto ja lisäys testeissä
        hairiotilanne-id (atom nil)
        materiaalilistaus-fn (fn [materiaalilistaukset nimi]
                               (first (filter #(and (= urakka-id (::vv-materiaali/urakka-id %))
                                                    (= nimi (::vv-materiaali/nimi %)))
                                              materiaalilistaukset)))
        q-materiaalimaara-fn (fn [q-vastaus nimi]
                               (apply +
                                      (map :maara
                                           (:muutokset (some #(when (= nimi (:nimi %)) %)
                                                             q-vastaus)))))
        hairiotilanteen-materiaalit-fn (fn [materiaalit hairiotilanne-id]
                                         (mapv (fn [materiaali]
                                                 (transduce
                                                   (comp
                                                     ;; otetaan vain yhden häiriötilanteen materiaalit
                                                     (filter (fn [muutos]
                                                               (= hairiotilanne-id (:hairiotilanne muutos))))
                                                     ;; annetaan lisää parametrejä tallennusta varten
                                                     (map (fn [muutos]
                                                            (assoc muutos :nimi (:nimi materiaali)
                                                                          :urakka-id urakka-id)))
                                                     ;; annetaan niille oikea ns keyword
                                                     (map (fn [muutos]
                                                            (namespacefy/namespacefy muutos {:ns :harja.domain.vesivaylat.materiaali}))))
                                                   conj (:muutokset materiaali)))
                                               materiaalit))]
    (testing "Materiaalin tallentuminen häiriötilanne lomakkeelta"
      (let [naulan-kulutus -10
            amparin-kulutus -20
            parametrit (tallennuksen-argumentit {:naulan-kulutus naulan-kulutus :amparin-kulutus amparin-kulutus})
            saimaan-materiaalit-ennen-tallennusta (hae-saimaan-kanavan-materiaalit)
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-hairiotilanne
                                    +kayttaja-jvh+
                                    parametrit)
            naula-materiaali (materiaalilistaus-fn (:materiaalilistaukset vastaus) "Naulat")
            ampari-materiaali (materiaalilistaus-fn (:materiaalilistaukset vastaus) "Ämpäreitä")
            naula-lkm-ennen (q-materiaalimaara-fn saimaan-materiaalit-ennen-tallennusta "Naulat")
            naula-lkm-jalkeen (::vv-materiaali/maara-nyt naula-materiaali)
            ampari-lkm-ennen (q-materiaalimaara-fn saimaan-materiaalit-ennen-tallennusta "Ämpäreitä")
            ampari-lkm-jalkeen (::vv-materiaali/maara-nyt ampari-materiaali)]
        (is (= naula-lkm-ennen (+ naula-lkm-jalkeen (Math/abs naulan-kulutus))))
        (is (= ampari-lkm-ennen (+ ampari-lkm-jalkeen (Math/abs amparin-kulutus))))))

    (testing "Materiaalin muokkaaminen häiriötilanne lomakkeelta"
      (let [saimaan-materiaalit-ennen-tallennusta (hae-saimaan-kanavan-materiaalit)
            tallennetut-hairiotilanteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :hae-hairiotilanteet
                                                        +kayttaja-jvh+
                                                        {::hairiotilanne/urakka-id urakka-id})
            naulan-kulutus -8
            amparin-kulutus -15
            naulan-ykiskko "kpl"
            amparin-yksikko "kpl"
            _ (reset! hairiotilanne-id (->> saimaan-materiaalit-ennen-tallennusta
                                            (some #(when (= (:nimi %) "Naulat") %))
                                            :muutokset
                                            (filter :hairiotilanne)
                                            first
                                            :hairiotilanne))
            hairiotilanteen-materiaalit (hairiotilanteen-materiaalit-fn saimaan-materiaalit-ennen-tallennusta @hairiotilanne-id)
            muokkausparametrit (->
                                 ;; ihan sama mitkä määrät annetaan materiaalille, sillä ne vaihdetaan kohta
                                 (tallennuksen-argumentit {})
                                 ;; annetaan hairiotilanne id, niin tulee päivitys eikä luoda uutta
                                 (assoc-in
                                   [::hairiotilanne/hairiotilanne ::hairiotilanne/id]
                                   @hairiotilanne-id)
                                 ;; annetaan häiriötilanteen materiaalit (voi olla useampi matsku testidatassa
                                 ;; kuin vain yksi setti nauloja ja yksi setti ämpäreitä)
                                 (assoc ::vv-materiaali/materiaalikirjaukset hairiotilanteen-materiaalit)
                                 ;; Lähetetään vain yhdet ämpäri- ja naulakirjaukset
                                 (update ::vv-materiaali/materiaalikirjaukset (fn [materiaalit]
                                                                                (flatten (map #(get % 0)
                                                                                              materiaalit))))
                                 ;; Muokataan backille lähtevää tavaraa
                                 (update ::vv-materiaali/materiaalikirjaukset (fn [materiaalit]
                                                                                (mapv #(-> %
                                                                                           ;; Poistetaan avaimet, joita ei lähetetä backille
                                                                                           (dissoc ::vv-materiaali/luotu ::vv-materiaali/hairiotilanne
                                                                                                   ::vv-materiaali/toimenpide)
                                                                                           ;; Annetaan kirjauksille halutut arvot
                                                                                           (assoc ::vv-materiaali/maara
                                                                                                    (case (::vv-materiaali/nimi %)
                                                                                                      "Naulat" naulan-kulutus
                                                                                                      "Ämpäreitä" amparin-kulutus)
                                                                                                    ::vv-materiaali/yksikko
                                                                                                    (case (::vv-materiaali/nimi %)
                                                                                                      "Naulat" naulan-ykiskko
                                                                                                      "Ämpäreitä" amparin-yksikko)))
                                                                                      materiaalit))))
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-hairiotilanne
                                    +kayttaja-jvh+
                                    muokkausparametrit)
            naula-materiaali (materiaalilistaus-fn (:materiaalilistaukset vastaus) "Naulat")
            ampari-materiaali (materiaalilistaus-fn (:materiaalilistaukset vastaus) "Ämpäreitä")
            naula-lkm-ennen (q-materiaalimaara-fn saimaan-materiaalit-ennen-tallennusta "Naulat")
            naula-lkm-jalkeen (::vv-materiaali/maara-nyt naula-materiaali)
            ampari-lkm-ennen (q-materiaalimaara-fn saimaan-materiaalit-ennen-tallennusta "Ämpäreitä")
            ampari-lkm-jalkeen (::vv-materiaali/maara-nyt ampari-materiaali)]
        (is (= naula-lkm-ennen (- naula-lkm-jalkeen 2)))
        (is (= ampari-lkm-ennen (- ampari-lkm-jalkeen 5)))))

    (testing "Materiaalin poistaminen häiriötilanne lomakkeelta"
      (let [saimaan-materiaalit-ennen-tallennusta (hae-saimaan-kanavan-materiaalit)
            tallennetut-hairiotilanteet (kutsu-palvelua (:http-palvelin jarjestelma)
                                                        :hae-hairiotilanteet
                                                        +kayttaja-jvh+
                                                        {::hairiotilanne/urakka-id urakka-id})
            hairiotilanteen-materiaalit (hairiotilanteen-materiaalit-fn saimaan-materiaalit-ennen-tallennusta @hairiotilanne-id)
            muokkausparametrit (->
                                 ;; ihan sama mitkä määrät annetaan materiaalille, sillä ne vaihdetaan kohta
                                 (tallennuksen-argumentit {})
                                 ;; annetaan hairiotilanne id, niin tulee päivitys eikä luoda uutta
                                 (assoc-in
                                   [::hairiotilanne/hairiotilanne ::hairiotilanne/id]
                                   @hairiotilanne-id)
                                 ;; ei anneta mitään tallennettavaa
                                 (assoc ::vv-materiaali/materiaalikirjaukset [])
                                 ;; Poistetaan häiriötilanteen kaikki ämpärin käytön kirjaukset
                                 (assoc ::vv-materiaali/poista-materiaalikirjauksia (flatten (mapv (fn [materiaalikirjaukset]
                                                                                                     (mapv #(if (= "Ämpäreitä" (::vv-materiaali/nimi %))
                                                                                                              (select-keys % #{::vv-materiaali/urakka-id
                                                                                                                               ::vv-materiaali/id})
                                                                                                              [])
                                                                                                           materiaalikirjaukset))
                                                                                                   hairiotilanteen-materiaalit))))
            vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                    :tallenna-hairiotilanne
                                    +kayttaja-jvh+
                                    muokkausparametrit)
            naula-materiaali (materiaalilistaus-fn (:materiaalilistaukset vastaus) "Naulat")
            ampari-materiaali (materiaalilistaus-fn (:materiaalilistaukset vastaus) "Ämpäreitä")
            naula-lkm-ennen (q-materiaalimaara-fn saimaan-materiaalit-ennen-tallennusta "Naulat")
            naula-lkm-jalkeen (::vv-materiaali/maara-nyt naula-materiaali)]
        (is (empty? (filter ::vv-materiaali/hairiotilanne (::vv-materiaali/muutokset ampari-materiaali))))
        (is (= naula-lkm-ennen naula-lkm-jalkeen))))))


(deftest hairiotilanteen-tallennus
  (let [syy (str "hairiotilanteen-tallennus-testi-" (UUID/randomUUID))
        parametrit (tallennuksen-argumentit {:syy syy})
        maara-ennen (ffirst (q "SELECT COUNT(*) FROM kan_hairio"))
        vastaus (kutsu-palvelua (:http-palvelin jarjestelma)
                                :tallenna-hairiotilanne
                                +kayttaja-jvh+
                                parametrit)
        maara-jalkeen (ffirst (q "SELECT COUNT(*) FROM kan_hairio"))]
    (is (some #(= syy (::hairiotilanne/syy %)) (:hairiotilanteet vastaus)))
    (is (+ maara-ennen 1) maara-jalkeen)))

(deftest hairiotilanteen-tallennus-vaaraan-urakkaan
  (let [paivitettava-id (ffirst (q "SELECT id FROM kan_hairio LIMIT 1"))
        syy (str "hairiotilanteen-tallennus-testi-" (UUID/randomUUID))
        oulun-urakka-2014 (hae-oulun-alueurakan-2014-2019-id)
        parametrit (tallennuksen-argumentit {:id paivitettava-id
                                             :syy syy
                                             :urakka-id oulun-urakka-2014})]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-hairiotilanne
                                                   +kayttaja-jvh+
                                                   parametrit)))))

(deftest hairiotilanteen-tallennus-vaaraan-kohteeseen
  (let [syy (str "hairiotilanteen-tallennus-testi-" (UUID/randomUUID))
        urakka-id (hae-saimaan-kanavaurakan-id)
        kohde-id 66666
        parametrit (tallennuksen-argumentit {:kohde-id kohde-id
                                             :syy syy
                                             :urakka-id urakka-id})]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-hairiotilanne
                                                   +kayttaja-jvh+
                                                   parametrit)))))

(deftest hairiotilanteen-tallennus-vaaraan-kohdeosaan
  (let [syy (str "hairiotilanteen-tallennus-testi-" (UUID/randomUUID))
        urakka-id (hae-saimaan-kanavaurakan-id)
        kohde-id (ffirst (q (str "SELECT \"kohde-id\" FROM kan_kohde_urakka WHERE \"urakka-id\" = " urakka-id ";")))
        kohdeosa-id (ffirst (q (str "SELECT id FROM kan_kohteenosa WHERE \"kohde-id\" != " kohde-id ";")))
        parametrit (tallennuksen-argumentit {:kohde-id kohde-id
                                             :kohdeosa-id kohdeosa-id
                                             :syy syy
                                             :urakka-id urakka-id})]

    (is (thrown? SecurityException (kutsu-palvelua (:http-palvelin jarjestelma)
                                                   :tallenna-hairiotilanne
                                                   +kayttaja-jvh+
                                                   parametrit)))))

(deftest hairiotilanteiden-tallennus-ilman-oikeuksia
  (let [parametrit (tallennuksen-argumentit {})]
    (is (thrown? Exception (kutsu-palvelua (:http-palvelin jarjestelma)
                                           :tallenna-hairiotilanne
                                           +kayttaja-ulle+
                                           parametrit)))))