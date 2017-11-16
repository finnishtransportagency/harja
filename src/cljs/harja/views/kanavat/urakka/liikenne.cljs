(ns harja.views.kanavat.urakka.liikenne
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.tiedot.kanavat.urakka.liikenne :as tiedot]
            [harja.loki :refer [tarkkaile! log]]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]

            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja]]
            [harja.ui.debug :refer [debug]]
            [harja.ui.modal :as modal]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.views.urakka.valinnat :as suodattimet]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :as kentat]
            [harja.id :refer [id-olemassa?]]

            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakka :as ur]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.sopimus :as sop]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.kanavan-kohde :as kohde]
            [harja.ui.ikonit :as ikonit])
  (:require-macros
    [cljs.core.async.macros :refer [go]]
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn liikenne-muokkausgrid [e! {:keys [valittu-liikennetapahtuma] :as app}]
  [grid/muokkaus-grid
   {:tyhja "Lisää tapahtumia oikeasta yläkulmasta"
    :tunniste ::lt-alus/id}
   [{:otsikko "Suunta"
     :tyyppi :komponentti
     :tasaa :keskita
     :komponentti (fn [rivi]
                    (let [suunta (::lt-alus/suunta rivi)]
                      [napit/yleinen-toissijainen
                       (lt/suunta->str suunta)
                       #(e! (tiedot/->VaihdaSuuntaa rivi))
                       {:ikoni (if (= :ylos suunta) (ikonit/livicon-arrow-up) (ikonit/livicon-arrow-down))}]))}
    {:otsikko "Aluslaji"
     :tyyppi :valinta
     :nimi ::lt-alus/laji
     :valinnat lt-alus/aluslajit
     :valinta-nayta #(or (lt-alus/aluslaji->str %)
                         (when (= :nippu %) "Nippu")
                         "- Valitse -")}
    {:otsikko "Nimi"
     :tyyppi :string
     :nimi ::lt-alus/nimi}
    {:otsikko "Kpl"
     :nimi ::lt-alus/lkm
     :tyyppi :positiivinen-numero}
    {:otsikko "Matkustajia"
     :nimi ::lt-alus/matkustajalkm
     :tyyppi :positiivinen-numero}
    {:otsikko "Nippuluku"
     :nimi ::lt-alus/nippulkm
     :tyyppi :positiivinen-numero}]
   (r/wrap
     (into {}
           (map-indexed
             (fn [i k] [i k])
             (::lt/alukset valittu-liikennetapahtuma)))
     #(e! (tiedot/->MuokkaaAluksia (vals %))))])

(defn varmistus-modal [sisalto footer]
  (modal/nayta! {:otsikko "Oletko varma?"
                 :footer footer}
                sisalto))

(defn liikennetapahtumalomake [e! {:keys [valittu-liikennetapahtuma
                                          tallennus-kaynnissa?
                                          urakan-kohteet] :as app}]
  [:div
   [debug app]
   [napit/takaisin "Takaisin" #(e! (tiedot/->ValitseTapahtuma nil))]
   [lomake/lomake
    {:otsikko (if (id-olemassa? (::lt/id valittu-liikennetapahtuma))
                "Muokkaa liikennetapahtumaa"
                "Luo uusi liikennetapahtuma")
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
                   [napit/poista
                    "Poista tapahtuma"
                    #(varmistus-modal
                       [:div "Oletko varma, että haluat poistaa koko liikennetapahtuman?"]
                       [:div
                        [napit/takaisin "Peruuta" (fn [] (modal/piilota!))]
                        [napit/poista
                         "Poista tapahtuma"
                         (fn []
                           (e! (tiedot/->TallennaLiikennetapahtuma
                                 (lomake/ilman-lomaketietoja (assoc tapahtuma ::m/poistettu? true)))))
                         {:ikoni (ikonit/livicon-trash)
                          :disabled (or tallennus-kaynnissa?
                                        (not (oikeudet/urakat-kanavat-liikenne))
                                        (not (tiedot/voi-tallentaa? tapahtuma))
                                        (not (lomake/voi-tallentaa? tapahtuma)))}]])
                    {:ikoni (ikonit/livicon-trash)
                     :disabled (or tallennus-kaynnissa?
                                   (not (oikeudet/urakat-kanavat-liikenne))
                                   (not (tiedot/voi-tallentaa? tapahtuma))
                                   (not (lomake/voi-tallentaa? tapahtuma)))}]
                   (when-not (id-olemassa? (::lt/id valittu-liikennetapahtuma))
                     [napit/yleinen-toissijainen
                      "Tyhjennä kentät"
                      #(e! (tiedot/->ValitseTapahtuma (tiedot/uusi-tapahtuma)))
                      {:ikoni (ikonit/refresh)
                       :disabled tallennus-kaynnissa?}])])}
    [(lomake/rivi
       {:otsikko "Kuittaaja"
        :nimi ::lt/kuittaaja
        :muokattava? (constantly false)
        :tyyppi :string
        :fmt kayttaja/kayttaja->str}
       {:otsikko "Sopimus"
        :nimi ::lt/sopimus
        :pakollinen? true
        :muokattava? #(if (id-olemassa? (::lt/id valittu-liikennetapahtuma)) false true)
        :tyyppi :valinta
        :valinta-nayta ::sop/nimi
        :valinnat (map (fn [[id nimi]] {::sop/id id ::sop/nimi nimi}) (:sopimukset @nav/valittu-urakka))
        :fmt ::sop/nimi
        :palstoja 1})
     (lomake/rivi
       {:otsikko "Aika"
        :nimi ::lt/aika
        :tyyppi :pvm-aika}
       {:otsikko "Kohde"
        :nimi ::lt/kohde
        :tyyppi :valinta
        :valinnat urakan-kohteet
        :pakollinen? true
        :valinta-nayta #(if % (kohde/fmt-kohteen-kanava-nimi %) "- Valitse kohde -")})
     (when (kohde/sulku? (::lt/kohde valittu-liikennetapahtuma))
       (lomake/rivi
         {:otsikko "Sulun toimenpide"
          :nimi ::lt/sulku-toimenpide
          :tyyppi :radio-group
          :vaihtoehdot lt/sulku-toimenpide-vaihtoehdot
          :vaihtoehto-nayta lt/sulku-toimenpide->str}
         {:otsikko "Palvelumuoto"
          :nimi ::lt/sulku-palvelumuoto
          :tyyppi :valinta
          :valinnat lt/palvelumuoto-vaihtoehdot
          :valinta-nayta #(if % (lt/palvelumuoto->str %) " - Valitse -")}
         {:otsikko "Kpl"
          :nimi ::lt/sulku-lkm
          :tyyppi :positiivinen-numero}))
     (when (kohde/silta? (::lt/kohde valittu-liikennetapahtuma))
       (lomake/rivi
         {:otsikko "Sillan avaus"
          :nimi ::lt/silta-avaus
          :tyyppi :radio-group
          :oletusarvo "Ei"
          :vaihtoehdot ["Kyllä" "Ei"]
          :hae (fn [rivi] (cond (true? (::lt/silta-avaus rivi)) "Kyllä"

                                (false? (::lt/silta-avaus rivi)) "Ei"

                                :else nil))
          :aseta (fn [rivi arvo]
                   (case arvo
                     "Kyllä"
                     (assoc rivi ::lt/silta-avaus true)

                     "Ei"
                     (assoc rivi ::lt/silta-avaus false)

                     (dissoc rivi ::lt/silta-avaus)))}
         {:otsikko "Palvelumuoto"
          :nimi ::lt/silta-palvelumuoto
          :tyyppi :valinta
          :valinnat lt/palvelumuoto-vaihtoehdot
          :valinta-nayta #(if % (lt/palvelumuoto->str %) " - Valitse -")}
         {:otsikko "Kpl"
          :nimi ::lt/sulku-lkm
          :tyyppi :positiivinen-numero}))
     (lomake/rivi
       {:otsikko "Alapinta"
        :tyyppi :positiivinen-numero
        :nimi ::lt/vesipinta-alaraja}
       {:otsikko "Yläpinta"
        :tyyppi :positiivinen-numero
        :nimi ::lt/vesipinta-ylaraja})
     {:otsikko "Liikenne"
      :tyyppi :komponentti
      :palstoja 3
      :nimi :muokattavat-tapahtumat
      :komponentti (fn [_] [liikenne-muokkausgrid e! app])}]
    valittu-liikennetapahtuma]])

(defn valinnat [e! {:keys [urakan-kohteet] :as app}]
  (let [atomi (partial tiedot/valinta-wrap e! app)]
    [valinnat/urakkavalinnat
     {}
     ^{:key "valinnat"}
     [suodattimet/urakan-sopimus-ja-hoitokausi-ja-aikavali @nav/valittu-urakka]
     [valinnat/kanava-kohde
      (atomi ::lt/kohde)
      (into [nil] urakan-kohteet)
      #(let [nimi (kohde/fmt-kohteen-kanava-nimi %)]
         (if-not (empty? nimi)
           nimi
           "Kaikki"))]
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Suunta"
       :kentta-params {:tyyppi :valinta
                       :valinnat (into [nil] lt/suunta-vaihtoehdot)
                       :valinta-nayta #(or (lt/suunta->str %) "Molemmat")}
       :arvo-atom (atomi ::lt-alus/suunta)}]
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Toimenpidetyyppi"
       :kentta-params {:tyyppi :valinta
                       :valinta-nayta #(or (lt/sulku-toimenpide->str %) "Kaikki")
                       :valinnat (into [nil] lt/sulku-toimenpide-vaihtoehdot)}
       :arvo-atom (atomi ::lt/toimenpide)}]
     [valinnat/kanava-aluslaji
      (atomi ::lt-alus/laji)
      (into [nil] lt-alus/aluslajit)
      #(or (lt-alus/aluslaji->str %) "Kaikki")]
     [kentat/tee-otsikollinen-kentta
      {:otsikko "Uittoniput?"
       :kentta-params {:tyyppi :checkbox}
       :arvo-atom (atomi :niput?)}]]))

(defn liikennetapahtumataulukko [e! {:keys [tapahtumarivit
                                            liikennetapahtumien-haku-kaynnissa?] :as app}]
  [:div
   [debug app]
   [valinnat e! app]
   [napit/uusi
    "Lisää tapahtuma"
    #(e! (tiedot/->ValitseTapahtuma (tiedot/uusi-tapahtuma)))]
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
      :nimi ::lt/aika
      :fmt pvm/pvm-aika-opt}
     {:otsikko "Kohde"
      :nimi :kohteen-nimi}
     {:otsikko "Tyyppi"
      :nimi :toimenpide
      :hae tiedot/toimenpide->str}
     {:otsikko "Suunta"
      :nimi :suunta
      :fmt lt/suunta->str}
     {:otsikko "Alus"
      :nimi ::lt-alus/nimi}
     {:otsikko "Aluslaji"
      :nimi ::lt-alus/laji
      :fmt lt-alus/aluslaji->str}
     {:otsikko "Matkustajia"
      :nimi ::lt-alus/matkustajalkm}
     {:otsikko "Aluksia"
      :nimi ::lt-alus/lkm}
     {:otsikko "Palvelumuoto"
      :nimi :palvelumuoto-ja-lkm
      :hae tiedot/palvelumuoto->str}
     {:otsikko "Nippuja"
      :nimi ::lt-alus/nippulkm}
     {:otsikko "Ylävesi"
      :nimi ::lt/vesipinta-ylaraja}
     {:otsikko "Alavesi"
      :nimi ::lt/vesipinta-alaraja}
     {:otsikko "Lisätiedot"
      :nimi ::lt/lisatieto}
     {:otsikko "Kuittaaja"
      :nimi :kuittaaja
      :hae (comp ::kayttaja/kayttajanimi ::lt/kuittaaja)}]
    (sort-by
      ;; Tarvitaan aika monta vaihtoehtoista sorttausavainta, koska
      ;; yhdelle kohteelle voi tulla yhdellä kirjauksella aika monta riviä
      (juxt ::lt/aika
            :kohteen-nimi
            ::lt/toimenpide
            ::lt-alus/laji
            ::lt-alus/nimi
            ::lt-alus/lkm)
      tapahtumarivit)]])

(defn liikenne* [e! app valinnat]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           ;; Valintojen päivittäminen laukaisee myös liikennetapahtumien haun
                           (e! (tiedot/->PaivitaValinnat {::ur/id (:urakka valinnat)
                                                          ::sop/id (:sopimus valinnat)
                                                          :aikavali (:aikavali valinnat)}))
                           (e! (tiedot/->HaeKohteet)))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! {:keys [valittu-liikennetapahtuma] :as app}]
      @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.
      (if-not valittu-liikennetapahtuma
        [liikennetapahtumataulukko e! app]
        [liikennetapahtumalomake e! app]))))

(defn liikennetapahtumat [e! app]
  [liikenne* e! app {:urakka (:id @nav/valittu-urakka)
                     :aikavali @u/valittu-aikavali
                     :sopimus (first @u/valittu-sopimusnumero)}])

(defc liikenne []
  [tuck tiedot/tila liikennetapahtumat])

