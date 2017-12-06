(ns harja.views.kanavat.urakka.liikenne
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]
            [cljs-time.core :as time]

            [harja.tiedot.kanavat.urakka.liikenne :as tiedot]
            [harja.loki :refer [tarkkaile! log]]
            [harja.pvm :as pvm]

            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.views.urakka.valinnat :as suodattimet]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :as kentat]
            [harja.ui.varmista-kayttajalta :refer [varmista-kayttajalta]]
            [harja.id :refer [id-olemassa?]]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.kanavat.urakka.kanavaurakka :as kanavaurakka]

            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakka :as ur]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.sopimus :as sop]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.lt-ketjutus :as ketjutus]
            [harja.domain.kanavat.lt-toiminto :as toiminto]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohteenosa :as osa]
            [clojure.string :as str]
            [harja.fmt :as fmt])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn edelliset-grid [e! {:keys [siirretyt-alukset ketjutuksen-poistot] :as app} {:keys [alukset] :as tiedot}]
  (let [kuitattavat (remove (comp (or siirretyt-alukset #{}) ::lt-alus/id) alukset)]
    [:div
     [grid/grid
      {:tunniste ::lt-alus/id
       :tyhja "Kaikki alukset siirretty"}
      [{:otsikko ""
        :tyyppi :komponentti
        :tasaa :keskita
        :komponentti (fn [alus]
                       [napit/yleinen-toissijainen
                        "Kuittaa"
                        #(e! (tiedot/->SiirraTapahtumaan alus))
                        {:ikoni (ikonit/livicon-arrow-bottom)}])}
       {:otsikko "Nimi"
        :tyyppi :string
        :nimi ::lt-alus/nimi}
       {:otsikko "Aluslaji"
        :tyyppi :string
        :nimi ::lt-alus/laji
        :fmt lt-alus/aluslaji->laji-str}
       {:otsikko "Alusten lkm"
        :nimi ::lt-alus/lkm
        :tyyppi :positiivinen-numero}
       {:otsikko "Matkustajia"
        :nimi ::lt-alus/matkustajalkm
        :tyyppi :positiivinen-numero}
       {:otsikko "Nippuluku"
        :nimi ::lt-alus/nippulkm
        :tyyppi :positiivinen-numero}
       {:otsikko "Edellinen tapahtuma"
        :nimi ::lt/aika
        :tyyppi :pvm-aika
        :fmt pvm/pvm-aika-opt}
       {:otsikko "Lisätiedot"
        :nimi ::lt/lisatieto
        :tyyppi :string}
       {:otsikko ""
        :nimi :poistettu
        :tyyppi :komponentti
        :komponentti (fn [rivi]
                       (if-not (ketjutuksen-poistot (::lt-alus/id rivi))
                         [:span.klikattava {:on-click
                                            #(do (.preventDefault %)
                                                 (e! (tiedot/->PoistaKetjutus rivi)))}
                          (ikonit/livicon-trash)]

                         [ajax-loader-pieni]))}]
      kuitattavat]
     [napit/yleinen-toissijainen
      "Kuittaa kaikki tapahtumaan"
      #(e! (tiedot/->SiirraKaikkiTapahtumaan kuitattavat))
      {:ikoni (ikonit/livicon-arrow-bottom)
       :disabled (empty? kuitattavat)}]]))

(defn liikenne-muokkausgrid [e! {:keys [valittu-liikennetapahtuma siirretyt-alukset] :as app}]
  [grid/muokkaus-grid
   {:tyhja "Lisää tapahtumia oikeasta yläkulmasta"
    :virheet-dataan? true
    :toimintonappi-fn (fn [rivi muokkaa!]
                        (when (siirretyt-alukset (::lt-alus/id rivi))
                          [:span.klikattava {:on-click
                                             #(do (.preventDefault %)
                                                  (e! (tiedot/->SiirraTapahtumasta rivi))
                                                  (muokkaa! (constantly nil)))}
                           (ikonit/livicon-arrow-top)]))}
   [{:otsikko "Suunta"
     :tyyppi :komponentti
     :tasaa :keskita
     :komponentti (fn [rivi]
                    (let [suunta (::lt-alus/suunta rivi)
                          valittu-suunta (:valittu-suunta valittu-liikennetapahtuma)]
                      [napit/yleinen-toissijainen
                       (lt/suunta->str suunta)
                       #(e! (tiedot/->VaihdaSuuntaa rivi))
                       {:ikoni (cond (= :ylos suunta) (ikonit/livicon-arrow-up)
                                     (= :alas suunta) (ikonit/livicon-arrow-down)
                                     :else (ikonit/livicon-question))
                        :disabled (some? (#{:ylos :alas} valittu-suunta))}]))}
    {:otsikko "Nimi"
     :tyyppi :string
     :nimi ::lt-alus/nimi}
    {:otsikko "Aluslaji"
     :tyyppi :valinta
     :nimi ::lt-alus/laji
     :validoi [[:ei-tyhja "Valitse aluslaji"]]
     :valinnat lt-alus/aluslajit
     :valinta-nayta #(or (lt-alus/aluslaji->koko-str %) "- Valitse -")}
    {:otsikko "Alusten lkm"
     :nimi ::lt-alus/lkm
     :muokattava? lt-alus/tayta-lukumaara?
     :oletusarvo 1
     :validoi [[:ei-tyhja "Syötä kappalemäärä"]]
     :tyyppi :positiivinen-numero}
    {:otsikko "Matkustajia"
     :nimi ::lt-alus/matkustajalkm
     :muokattava? lt-alus/tayta-matkustajamaara?
     :tyyppi :positiivinen-numero}
    {:otsikko "Nippuluku"
     :nimi ::lt-alus/nippulkm
     :muokattava? lt-alus/tayta-nippuluku?
     :tyyppi :positiivinen-numero}]
   (r/wrap
     (into {}
           (map-indexed
             (fn [i k] [i k])
             (::lt/alukset valittu-liikennetapahtuma)))
     #(e! (tiedot/->MuokkaaAluksia (vals %) (boolean (some
                                                       (comp not empty? ::grid/virheet)
                                                       (remove :poistettu (vals %)))))))])

(defn liikennetapahtumalomake [e! {:keys [valittu-liikennetapahtuma
                                          tallennus-kaynnissa?
                                          edellisten-haku-kaynnissa?
                                          edelliset] :as app}
                               kohteet]
  (let [uusi-tapahtuma? (not (id-olemassa? (::lt/id valittu-liikennetapahtuma)))]
    [:div
     [debug app]
     [napit/takaisin "Takaisin" #(e! (tiedot/->ValitseTapahtuma nil))]
     [lomake/lomake
      {:otsikko (if uusi-tapahtuma?
                  "Luo uusi liikennetapahtuma"
                  "Muokkaa liikennetapahtumaa")
       :muokkaa! #(e! (tiedot/->TapahtumaaMuokattu (lomake/ilman-lomaketietoja %)))
       :voi-muokata? (oikeudet/urakat-kanavat-liikenne)
       :footer-fn (fn [tapahtuma]
                    [:div
                     [napit/tallenna
                      "Tallenna liikennetapahtuma"
                      #(e! (tiedot/->TallennaLiikennetapahtuma (lomake/ilman-lomaketietoja tapahtuma)))
                      {:ikoni (ikonit/tallenna)
                       :disabled (or tallennus-kaynnissa?
                                     (not (oikeudet/urakat-kanavat-liikenne))
                                     (not (tiedot/voi-tallentaa? tapahtuma))
                                     (not (lomake/voi-tallentaa? tapahtuma)))}]
                     (when-not uusi-tapahtuma?
                       [napit/poista
                        "Poista tapahtuma"
                        #(varmista-kayttajalta
                           {:otsikko "Poista tapahtuma"
                            :sisalto [:div "Oletko varma, että haluat poistaa koko liikennetapahtuman?"]
                            :hyvaksy "Poista tapahtuma"
                            :toiminto-fn (fn []
                                           (e! (tiedot/->TallennaLiikennetapahtuma
                                                 (lomake/ilman-lomaketietoja (assoc tapahtuma ::m/poistettu? true)))))
                            :napit [:takaisin :poista]})
                        {:ikoni (ikonit/livicon-trash)
                         :disabled (or tallennus-kaynnissa?
                                       (not (oikeudet/urakat-kanavat-liikenne))
                                       (not (lomake/voi-tallentaa? tapahtuma)))}])
                     (when uusi-tapahtuma?
                       [napit/yleinen-toissijainen
                        "Tyhjennä kentät"
                        #(varmista-kayttajalta
                           {:otsikko "Tyhjennä kentät"
                            :sisalto [:div "Oletko varma, että haluat tyhjentää kaikki kentät?"]
                            :hyvaksy "Tyhjennä"
                            :toiminto-fn (fn [] (e! (tiedot/->ValitseTapahtuma (tiedot/uusi-tapahtuma))))
                            :napit [:takaisin :hyvaksy]})
                        {:ikoni (ikonit/refresh)
                         :disabled tallennus-kaynnissa?}])])}
      (flatten
        [(lomake/rivi
           {:otsikko "Kuittaaja"
            :nimi ::lt/kuittaaja
            :muokattava? (constantly false)
            :tyyppi :string
            :fmt kayttaja/kayttaja->str}
           {:otsikko "Sopimus"
            :nimi ::lt/sopimus
            :pakollinen? true
            :muokattava? #(if uusi-tapahtuma? true false)
            :tyyppi :valinta
            :valinta-nayta ::sop/nimi
            :valinnat (map (fn [[id nimi]] {::sop/id id ::sop/nimi nimi}) (:sopimukset @nav/valittu-urakka))
            :fmt ::sop/nimi
            :palstoja 1})
         (lomake/rivi
           {:otsikko "Aika"
            :nimi ::lt/aika
            :tyyppi :pvm-aika
            :pakollinen? true}
           {:otsikko "Kohde"
            :nimi ::lt/kohde
            :tyyppi :valinta
            :valinnat kohteet
            :pakollinen? true
            :muokattava? #(if uusi-tapahtuma? true false)
            :valinta-nayta #(if % (kohde/fmt-kohteen-nimi %) "- Valitse kohde -")
            :aseta (fn [rivi arvo]
                     (let [rivi (-> rivi
                                    (tiedot/kohteenosatiedot-toimintoihin arvo)
                                    (tiedot/aseta-suunta arvo))]
                       (when uusi-tapahtuma?
                         (e! (tiedot/->HaeEdellisetTiedot rivi)))
                       rivi))})
         (map-indexed
           (fn [i osa]
             ^{:key (str "palvelumuoto-" i)}
             (lomake/ryhma
               {:otsikko (kohde/fmt-kohteenosan-nimi osa)
                :rivi? true}
               {:otsikko "Toimenpide"
                :nimi (str i "-toimenpide")
                :pakollinen? true
                :tyyppi :radio-group
                :vaihtoehdot (lt/toimenpide-vaihtoehdot osa)
                :vaihtoehto-nayta lt/toimenpide->str
                :hae (constantly (::toiminto/toimenpide osa))
                :aseta (fn [rivi arvo]
                         (tiedot/paivita-toiminnon-tiedot rivi (assoc osa ::toiminto/toimenpide arvo)))}
               (when-not (= (::toiminto/toimenpide osa) :ei-avausta)
                 {:otsikko "Palvelumuoto"
                  :nimi (str i "-palvelumuoto")
                  :pakollinen? true
                  :tyyppi :valinta
                  :valinnat lt/palvelumuoto-vaihtoehdot
                  :valinta-nayta #(if % (lt/palvelumuoto->str %) " - Valitse -")
                  :hae (constantly (::toiminto/palvelumuoto osa))
                  :aseta (fn [rivi arvo]
                           (tiedot/paivita-toiminnon-tiedot rivi (assoc osa ::toiminto/palvelumuoto arvo)))})
               (when (and (not= (::toiminto/toimenpide osa) :ei-avausta)
                          (= (::toiminto/palvelumuoto osa) :itse))
                 {:otsikko "Itsepalveluiden lukumäärä"
                  :nimi (str i "-lkm")
                  :pakollinen? true
                  :tyyppi :positiivinen-numero
                  :hae (constantly (::toiminto/lkm osa))
                  :aseta (fn [rivi arvo]
                           (tiedot/paivita-toiminnon-tiedot rivi (assoc osa ::toiminto/lkm arvo)))})))
           (::lt/toiminnot valittu-liikennetapahtuma))
         (when (::lt/kohde valittu-liikennetapahtuma)
           (apply lomake/rivi
             (concat
               (if (and edellisten-haku-kaynnissa? uusi-tapahtuma?)
                 [{:otsikko ""
                   :nimi :spinneri
                   :tyyppi :komponentti
                   :komponentti (fn [] [ajax-loader "Haetaan edellisiä tietoja"])}]

                 [{:otsikko "Alapinta"
                   :tyyppi :positiivinen-numero
                   :nimi ::lt/vesipinta-alaraja}
                  {:otsikko "Yläpinta"
                   :tyyppi :positiivinen-numero
                   :nimi ::lt/vesipinta-ylaraja}])

               (when (and uusi-tapahtuma?
                          (tiedot/tapahtuman-kohde-sisaltaa-sulun? valittu-liikennetapahtuma))
                 [{:otsikko "Suunta"
                   :nimi :valittu-suunta
                   :tyyppi :radio-group
                   :vaihtoehdot lt/suunta-vaihtoehdot
                   :vaihtoehto-nayta (fn [suunta]
                                       (str (lt/suunta->str suunta)
                                            (when (suunta edelliset)
                                              (str ", " (count (get-in edelliset [suunta :alukset])) " lähestyvää alusta"))))}]))))
         (when (and (::lt/kohde valittu-liikennetapahtuma)
                    (not edellisten-haku-kaynnissa?)
                    (or (:alas edelliset) (:ylos edelliset))
                    (some? (:valittu-suunta valittu-liikennetapahtuma))
                    uusi-tapahtuma?)
           (for* [[suunta tiedot] (dissoc edelliset :tama)]
             (when (and (some? tiedot)
                        (or (= suunta (:valittu-suunta valittu-liikennetapahtuma))
                            (= :molemmat (:valittu-suunta valittu-liikennetapahtuma))))
               (lomake/rivi
                 {:otsikko (str "Kohteelta " (::kohde/nimi tiedot) " (suuntana " (str/lower-case (lt/suunta->str suunta)) " )")
                  :tyyppi :komponentti
                  :palstoja 3
                  :nimi :edelliset-alukset
                  :komponentti (fn [_] [edelliset-grid e! app tiedot])}))))
         (when (and (::lt/kohde valittu-liikennetapahtuma)
                    (or (not uusi-tapahtuma?)
                        (:valittu-suunta valittu-liikennetapahtuma)))
           {:otsikko "Liikenne "
            :tyyppi :komponentti
            :palstoja 3
            :nimi :muokattavat-tapahtumat
            :komponentti (fn [_] [liikenne-muokkausgrid e! app])})
         (when (and (::lt/kohde valittu-liikennetapahtuma)
                    (or (not uusi-tapahtuma?)
                        (:valittu-suunta valittu-liikennetapahtuma)))
           {:otsikko "Lisätietoja"
           :tyyppi :text
           :nimi ::lt/lisatieto})])
      valittu-liikennetapahtuma]]))

(defn valinnat [e! app kohteet]
  (let [atomi (partial tiedot/valinta-wrap e! app)]
    [valinnat/urakkavalinnat
     {}
     ^{:key "valinnat"}
     [valinnat/valintaryhmat-3
      [suodattimet/urakan-sopimus-ja-hoitokausi-ja-aikavali @nav/valittu-urakka]

      [:div
       [valinnat/kanava-kohde
        (atomi ::lt/kohde)
        (into [nil] kohteet)
        #(let [nimi (kohde/fmt-kohteen-nimi %)]
           (if-not (empty? nimi)
             nimi
             "Kaikki"))]
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Sulun toimenpide"
         :kentta-params {:tyyppi :valinta
                         :valinta-nayta #(or (lt/sulku-toimenpide->str %) "Kaikki")
                         :valinnat (into [nil] lt/sulku-toimenpide-vaihtoehdot)}
         :arvo-atom (atomi ::lt/sulku-toimenpide)}]]

      [:div
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Suunta"
         :kentta-params {:tyyppi :valinta
                         :valinnat (into [nil] lt/suunta-vaihtoehdot)
                         :valinta-nayta #(or (lt/suunta->str %) "Molemmat")}
         :arvo-atom (atomi ::lt-alus/suunta)}]
       [valinnat/kanava-aluslaji
        (atomi ::lt-alus/laji)
        (into [nil] lt-alus/aluslajit)
        #(or (lt-alus/aluslaji->koko-str %) "Kaikki")]
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Uittoniput?"
         :kentta-params {:tyyppi :checkbox}
         :arvo-atom (atomi :niput?)}]]]
     [valinnat/urakkatoiminnot {:urakka @nav/valittu-urakka}
      [napit/uusi
       "Lisää tapahtuma"
       #(e! (tiedot/->ValitseTapahtuma (tiedot/uusi-tapahtuma)))]]]))

(defn liikennetapahtumataulukko [e! {:keys [tapahtumarivit liikennetapahtumien-haku-kaynnissa?] :as app}
                                 kohteet]
  [:div
   [debug app]
   [valinnat e! app kohteet]
   [grid/grid
    {:otsikko (if liikennetapahtumien-haku-kaynnissa?
                [ajax-loader-pieni "Päivitetään listaa.."]
                "Liikennetapahtumat")
     :tunniste (juxt ::lt/id ::lt-alus/id)
     :rivi-klikattu #(e! (tiedot/->ValitseTapahtuma %))
     :tyhja (if liikennetapahtumien-haku-kaynnissa?
              [ajax-loader "Haku käynnissä"]
              "Ei liikennetapahtumia")}
    [{:otsikko "Aika"
      :leveys 2
      :nimi ::lt/aika
      :fmt pvm/pvm-aika-opt}
     {:otsikko "Kohde"
      :leveys 5
      :nimi ::lt/kohde
      :fmt kohde/fmt-kohteen-nimi}
     {:otsikko "Tyyppi"
      :leveys 2
      :nimi :toimenpide
      :hae tiedot/toimenpide->str}
     {:otsikko "Sillan avaus"
      :leveys 1
      :nimi :sillan-avaus?
      :hae tiedot/silta-avattu?
      :fmt fmt/totuus-ikoni}
     {:otsikko "Palvelumuoto"
      :leveys 3
      :nimi :palvelumuoto-ja-lkm
      :hae tiedot/palvelumuoto->str}
     {:otsikko "Suunta"
      :leveys 1
      :nimi ::lt-alus/suunta
      :fmt lt/suunta->str}
     {:otsikko "Alus"
      :leveys 1
      :nimi ::lt-alus/nimi}
     {:otsikko "Aluslaji"
      :leveys 1
      :nimi ::lt-alus/laji
      :fmt lt-alus/aluslaji->laji-str}
     {:otsikko "Aluksia"
      :leveys 1
      :nimi ::lt-alus/lkm}
     {:otsikko "Matkustajia"
      :leveys 1
      :nimi ::lt-alus/matkustajalkm}
     {:otsikko "Nippuja"
      :leveys 1
      :nimi ::lt-alus/nippulkm}
     {:otsikko "Ylävesi"
      :leveys 1
      :nimi ::lt/vesipinta-ylaraja}
     {:otsikko "Alavesi"
      :leveys 1
      :nimi ::lt/vesipinta-alaraja}
     {:otsikko "Lisätiedot"
      :leveys 2
      :nimi ::lt/lisatieto}
     {:otsikko "Kuittaaja"
      :leveys 2
      :nimi ::lt/kuittaaja
      :fmt kayttaja/kayttaja->str}]
    (sort-by
      ;; Tarvitaan aika monta vaihtoehtoista sorttausavainta, koska
      ;; yhdelle kohteelle voi tulla yhdellä kirjauksella aika monta riviä
      (juxt ::lt/aika
            :kohteen-nimi
            ::lt/toimenpide
            ::lt-alus/laji
            ::lt-alus/nimi
            ::lt-alus/lkm)
      (fn [[a-aika & _ :as a] [b-aika & _ :as b]]
        (if (time/equal? a-aika b-aika)
          (compare a b)
          (time/after? a-aika b-aika)))
      tapahtumarivit)]])

(defn liikenne* [e! app valinnat]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           ;; Valintojen päivittäminen laukaisee myös liikennetapahtumien haun
                           (e! (tiedot/->PaivitaValinnat {::ur/id (:urakka valinnat)
                                                          ::sop/id (:sopimus valinnat)
                                                          :aikavali (:aikavali valinnat)})))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! {:keys [valittu-liikennetapahtuma] :as app}]
      @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.
      (if-not valittu-liikennetapahtuma
        [liikennetapahtumataulukko e! app @kanavaurakka/kanavakohteet]
        [liikennetapahtumalomake e! app @kanavaurakka/kanavakohteet]))))

(defn liikennetapahtumat [e! app]
  [liikenne* e! app {:urakka (:id @nav/valittu-urakka)
                     :aikavali @u/valittu-aikavali
                     :sopimus (first @u/valittu-sopimusnumero)}])

(defc liikenne []
  [tuck tiedot/tila liikennetapahtumat])

