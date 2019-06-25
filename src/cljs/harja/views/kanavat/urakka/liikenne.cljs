(ns harja.views.kanavat.urakka.liikenne
  (:require [clojure.string :as str]
            [reagent.core :refer [atom] :as r]
            [tuck.core :refer [tuck]]

            [harja.fmt :as fmt]
            [harja.loki :refer [tarkkaile! log]]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni tietoja totuus-ikoni] :as yleiset]
            [harja.ui.debug :refer [debug]]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.napit :as napit]
            [harja.ui.kentat :as kentat]
            [harja.ui.varmista-kayttajalta :refer [varmista-kayttajalta]]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.kanavat.urakka.kanavaurakka :as kanavaurakka]
            [harja.tiedot.kanavat.urakka.liikenne :as tiedot]
            [harja.views.urakka.valinnat :as suodattimet]

            [harja.domain.kayttaja :as kayttaja]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.urakka :as ur]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.sopimus :as sop]
            [harja.domain.kanavat.liikennetapahtuma :as lt]
            [harja.domain.kanavat.lt-alus :as lt-alus]
            [harja.domain.kanavat.lt-toiminto :as toiminto]
            [harja.domain.kanavat.kohde :as kohde]
            [harja.domain.kanavat.kohteenosa :as osa]
            [harja.tiedot.raportit :as raportit])
  (:require-macros
    [harja.makrot :refer [defc fnc]]
    [harja.tyokalut.ui :refer [for*]]))

(defn edelliset-grid [e! app {:keys [edelliset-alukset]}]
  (let [kuitattavat (tiedot/kuittausta-odottavat app edelliset-alukset)]
    [:div
     [grid/grid
      {:tunniste ::lt-alus/id
       :tyhja "Kaikki alukset siirretty"}
      [{:otsikko ""
        :tyyppi :komponentti
        :tasaa :keskita
        :komponentti (fn [alus]
                       [napit/yleinen-toissijainen
                        "Siirrä"
                        #(e! (tiedot/->SiirraTapahtumaan alus))
                        {:ikoni (ikonit/livicon-arrow-bottom)}])
        :leveys 1}
       {:otsikko "Nimi"
        :tyyppi :string
        :nimi ::lt-alus/nimi
        :leveys 1}
       {:otsikko "Aluslaji"
        :tyyppi :string
        :nimi ::lt-alus/laji
        :fmt lt-alus/aluslaji->laji-str
        :leveys 1}
       {:otsikko "Alusten lkm"
        :nimi ::lt-alus/lkm
        :tyyppi :positiivinen-numero
        :leveys 1}
       {:otsikko "Matkustajia"
        :nimi ::lt-alus/matkustajalkm
        :tyyppi :positiivinen-numero
        :leveys 1}
       {:otsikko "Nippuluku"
        :nimi ::lt-alus/nippulkm
        :tyyppi :positiivinen-numero
        :leveys 1}
       {:otsikko "Edellinen tapahtuma"
        :nimi ::lt/aika
        :tyyppi :pvm-aika
        :fmt pvm/pvm-aika-opt
        :leveys 1}
       {:otsikko "Lisätiedot"
        :nimi ::lt/lisatieto
        :tyyppi :string
        :leveys 1}
       {:otsikko ""
        :nimi :poistettu
        :tyyppi :komponentti
        :komponentti (fn [alus]
                       (if-not (tiedot/ketjutuksen-poisto-kaynnissa? app alus)
                         [:span.klikattava {:on-click
                                            #(do (.preventDefault %)
                                                 (e! (tiedot/->PoistaKetjutus alus)))}
                          (ikonit/livicon-trash)]

                         [ajax-loader-pieni]))
        :leveys 1}]
      kuitattavat]
     [napit/yleinen-toissijainen
      "Siirrä kaikki tapahtumaan"
      #(e! (tiedot/->SiirraKaikkiTapahtumaan kuitattavat))
      {:ikoni (ikonit/livicon-arrow-bottom)
       :disabled (empty? kuitattavat)}]]))

(defn liikenne-muokkausgrid [e! {:keys [valittu-liikennetapahtuma] :as app}]
  [grid/muokkaus-grid
   {:tyhja "Lisää tapahtumia oikeasta yläkulmasta"
    :virheet-dataan? true
    :toimintonappi-fn (fn [alus muokkaa!]
                        (when (tiedot/ketjutuksen-voi-siirtaa-tapahtumasta? app alus)
                          [:span.klikattava {:on-click
                                             #(do (.preventDefault %)
                                                  (e! (tiedot/->SiirraTapahtumasta alus))
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
                        :luokka "nappi-grid"
                        :disabled (some? (#{:ylos :alas} valittu-suunta))}]))
     :leveys 1}
    {:otsikko "Nimi"
     :tyyppi :string
     :nimi ::lt-alus/nimi
     :leveys 1}
    {:otsikko "Aluslaji"
     :tyyppi :valinta
     :nimi ::lt-alus/laji
     :validoi [[:ei-tyhja "Valitse aluslaji"]]
     :valinnat lt-alus/aluslajit
     :valinta-nayta #(or (lt-alus/aluslaji->koko-str %) "- Valitse -")
     :leveys 1}
    {:otsikko "Alusten lkm"
     :nimi ::lt-alus/lkm
     :oletusarvo 1
     :validoi [[:ei-tyhja "Syötä kappalemäärä"]]
     :tyyppi :positiivinen-numero
     :leveys 1}
    {:otsikko "Matkustajia"
     :nimi ::lt-alus/matkustajalkm
     :tyyppi :positiivinen-numero
     :leveys 1}
    {:otsikko "Nippuluku"
     :nimi ::lt-alus/nippulkm
     :tyyppi :positiivinen-numero
     :leveys 1}]
   (r/wrap
     (zipmap (range) (::lt/alukset valittu-liikennetapahtuma))
     #(e! (tiedot/->MuokkaaAluksia (vals %) (tiedot/grid-virheita? %))))])

(defn liikennetapahtumalomake [e! {:keys [valittu-liikennetapahtuma
                                          tallennus-kaynnissa?
                                          edellisten-haku-kaynnissa?
                                          edelliset] :as app}
                               kohteet]
  (let [uusi-tapahtuma? (not (id-olemassa? (::lt/id valittu-liikennetapahtuma)))]
    [:div
     [debug app]
     [napit/takaisin "Takaisin tapahtumaluetteloon" #(e! (tiedot/->ValitseTapahtuma nil))]
     [lomake/lomake
      {:otsikko (if uusi-tapahtuma?
                  "Liikennetapahtuman kirjaus"
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
      (concat
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
                       rivi))})]
        (map-indexed
          (fn [i osa]
            ^{:key (str "palvelumuoto-" i)}
            (lomake/ryhma
              {:otsikko (osa/fmt-kohteenosa osa)
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
              (when (tiedot/nayta-palvelumuoto? osa)
                {:otsikko "Palvelumuoto"
                 :nimi (str i "-palvelumuoto")
                 :pakollinen? true
                 :tyyppi :valinta
                 :valinnat lt/palvelumuoto-vaihtoehdot
                 :valinta-nayta #(if % (lt/palvelumuoto->str %) " - Valitse -")
                 :hae (constantly (::toiminto/palvelumuoto osa))
                 :aseta (fn [rivi arvo]
                          (tiedot/paivita-toiminnon-tiedot rivi (assoc osa ::toiminto/palvelumuoto arvo)))})
              (when (tiedot/nayta-itsepalvelut? osa)
                {:otsikko "Itsepalveluiden lukumäärä"
                 :nimi (str i "-lkm")
                 :pakollinen? true
                 :tyyppi :positiivinen-numero
                 :hae (constantly (::toiminto/lkm osa))
                 :aseta (fn [rivi arvo]
                          (tiedot/paivita-toiminnon-tiedot rivi (assoc osa ::toiminto/lkm arvo)))})))
          (::lt/toiminnot valittu-liikennetapahtuma))
        [(when (::lt/kohde valittu-liikennetapahtuma)
           (apply lomake/rivi
                  (concat
                    (if (and edellisten-haku-kaynnissa? uusi-tapahtuma?)
                      [{:otsikko ""
                        :nimi :spinneri
                        :tyyppi :komponentti
                        :komponentti (fn [] [ajax-loader "Haetaan edellisiä tietoja"])}]

                      [{:otsikko "Alapinta"
                        :tyyppi :numero
                        :desimaalien-maara 2
                        :nimi ::lt/vesipinta-alaraja}
                       {:otsikko "Yläpinta"
                        :tyyppi  :numero
                        :desimaalien-maara 2
                        :nimi ::lt/vesipinta-ylaraja}])

                    (when (and uusi-tapahtuma?
                               (tiedot/tapahtuman-kohde-sisaltaa-sulun? valittu-liikennetapahtuma))
                      [{:otsikko "Suunta"
                        :nimi :valittu-suunta
                        :tyyppi :radio-group
                        :vaihtoehdot lt/suunta-vaihtoehdot
                        :vaihtoehto-nayta (partial tiedot/suuntavalinta-str edelliset)
                        :aseta (fn [rivi arvo]
                                 (:valittu-liikennetapahtuma (e! (tiedot/->AsetaSuunnat arvo))))}]))))]
        (when (tiedot/nayta-edelliset-alukset? app)
          (for* [[suunta tiedot] (dissoc edelliset :tama)]
            (when (tiedot/nayta-suunnan-ketjutukset? app suunta tiedot)
              (lomake/rivi
                {:otsikko (str "Saapuva liikenne - Kohteelta " (::kohde/nimi tiedot) " (suuntana " (str/lower-case (lt/suunta->str suunta)) ")")
                 :tyyppi :komponentti
                 :palstoja 3
                 :nimi :edelliset-alukset
                 :komponentti (fn [_] [edelliset-grid e! app tiedot])}))))
        [(when (tiedot/nayta-liikennegrid? app)
           {:otsikko (str "Liikenne - " (get-in valittu-liikennetapahtuma [::lt/kohde ::kohde/nimi]))
            :tyyppi :komponentti
            :palstoja 3
            :nimi :muokattavat-tapahtumat
            :komponentti (fn [_] [liikenne-muokkausgrid e! app])})
         (when (tiedot/nayta-lisatiedot? app)
           {:otsikko "Lisätietoja"
            :tyyppi :text
            :nimi ::lt/lisatieto})])
      valittu-liikennetapahtuma]]))

(defn valinnat [e! {{:keys [kayttajan-urakat]} :valinnat :as app} kohteet]
  (let [atomi (partial tiedot/valinta-wrap e! app)]
    [valinnat/urakkavalinnat
     {}
     ^{:key "valinnat"}
     [valinnat/valintaryhmat-3
      [:div
       [:span.label-ja-kentta
        [:span.kentan-otsikko "Urakat"]
        [:div.kentta
         [yleiset/livi-pudotusvalikko
          {:naytettava-arvo (let [valittujen-urakoiden-maara (count (filter :valittu? kayttajan-urakat))]
                              (str valittujen-urakoiden-maara (if (= 1 valittujen-urakoiden-maara)
                                                                " urakka valittu"
                                                                " urakkaa valittu")))
           :itemit-komponentteja? true}
          (mapv (fn [urakka]
                  [:span
                   (:nimi urakka)
                   [:input {:style {:display "inline-block"
                                    :float "right"}
                            :type "checkbox"
                            :checked (:valittu? urakka)
                            :on-change #(let [valittu? (-> % .-target .-checked)]
                                          (e! (tiedot/->UrakkaValittu urakka valittu?)))}]])
                kayttajan-urakat)]]]
       [valinnat/aikavali (atomi :aikavali)]
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Aluksen nimi"
         :kentta-params {:tyyppi :string}
         :arvo-atom (atomi ::lt-alus/nimi)}]]

      [:div
       [valinnat/kanava-kohde
        (atomi ::lt/kohde)
        (into [nil] kohteet)
        #(let [nimi (kohde/fmt-kohteen-nimi %)]
           (if-not (empty? nimi)
             nimi
             "Kaikki"))]
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Aluslaji"
         :kentta-params {:tyyppi :checkbox-group
                         :palstoja 2
                         :vaihtoehdot lt-alus/aluslajit
                         :vaihtoehto-nayta lt-alus/aluslaji->laji-str}
         :arvo-atom (atomi ::lt-alus/aluslajit)}]
       ]
      [:div
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Suunta"
         :kentta-params {:tyyppi :valinta
                         :valinnat (into [nil] lt/suunta-vaihtoehdot)
                         :valinta-nayta #(or (lt/suunta->str %) "Molemmat")}
         :arvo-atom (atomi ::lt-alus/suunta)}]
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Uittoniput"
         :kentta-params {:tyyppi :checkbox
                         :teksti "Näytä vain uittoniput"}
         :arvo-atom (atomi :niput?)}]
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Toimenpidetyyppi"
         :kentta-params {:tyyppi :checkbox-group
                         :vaihtoehdot lt/sulku-toimenpide-vaihtoehdot
                         :vaihtoehto-nayta lt/sulku-toimenpide->str}
         :arvo-atom (atomi ::toiminto/toimenpiteet)}]]]
     [valinnat/urakkatoiminnot {:urakka @nav/valittu-urakka}
      [napit/uusi
       "Kirjaa liikennetapahtuma"
       #(e! (tiedot/->ValitseTapahtuma (tiedot/uusi-tapahtuma)))]]]))

(def liikennetapahtumat-sarakkeet
  [{:otsikko "Aika"
    :leveys 3
    :nimi ::lt/aika
    :fmt pvm/pvm-aika-opt}
   {:otsikko "Kohde"
    :leveys 3
    :nimi ::lt/kohde
    :fmt kohde/fmt-kohteen-nimi}
   {:otsikko "Tyyppi"
    :leveys 3
    :nimi :toimenpide
    :hae tiedot/toimenpide->str}
   {:otsikko "Sil\u00ADlan ava\u00ADus"
    :leveys 1
    :nimi :sillan-avaus?
    :hae tiedot/silta-avattu?
    :fmt totuus-ikoni}
   {:otsikko "Pal\u00ADvelu\u00ADmuoto"
    :leveys 3
    :nimi :palvelumuoto-ja-lkm
    :hae tiedot/palvelumuoto->str}
   {:otsikko "Suun\u00ADta"
    :leveys 2
    :nimi ::lt-alus/suunta
    :fmt lt/suunta->str}
   {:otsikko "Alus"
    :leveys 3
    :nimi ::lt-alus/nimi}
   {:otsikko "Alus\u00ADlaji"
    :leveys 2
    :nimi ::lt-alus/laji
    :fmt lt-alus/aluslaji->laji-str}
   {:otsikko "Aluk\u00ADsia"
    :leveys 1
    :nimi ::lt-alus/lkm}
   {:otsikko "Mat\u00ADkus\u00ADta\u00ADji\u00ADa"
    :leveys 1
    :nimi ::lt-alus/matkustajalkm}
   {:otsikko "Nip\u00ADpu\u00ADja"
    :leveys 1
    :nimi ::lt-alus/nippulkm}
   {:otsikko "Ylä\u00ADvesi"
    :leveys 2
    :nimi ::lt/vesipinta-ylaraja}
   {:otsikko "Ala\u00ADvesi"
    :leveys 2
    :nimi ::lt/vesipinta-alaraja}
   {:otsikko "Lisä\u00ADtiedot"
    :leveys 5
    :nimi ::lt/lisatieto}
   {:otsikko "Kuit\u00ADtaaja"
    :leveys 3
    :nimi ::lt/kuittaaja
    :fmt kayttaja/kayttaja->str}])


(defn liikennetapahtumataulukko [e! {:keys [tapahtumarivit liikennetapahtumien-haku-kaynnissa?
                                            liikennetapahtumien-haku-tulee-olemaan-kaynnissa?
                                            raporttiparametrit] :as app}
                                 kohteet]
  [:div
   [debug app]
   [valinnat e! app kohteet]
   [grid/grid
    {:otsikko (if (or liikennetapahtumien-haku-kaynnissa? liikennetapahtumien-haku-tulee-olemaan-kaynnissa?)
                [ajax-loader-pieni "Päivitetään listaa.."]
                "Liikennetapahtumat")
     :tunniste (juxt ::lt/id ::lt-alus/id)
     :sivuta grid/vakiosivutus
     :rivi-klikattu #(e! (tiedot/->ValitseTapahtuma %))
     :tyhja (if (or liikennetapahtumien-haku-kaynnissa? liikennetapahtumien-haku-tulee-olemaan-kaynnissa?)
              [ajax-loader "Haku käynnissä"]
              "Ei liikennetapahtumia")
     :raporttivienti #{:excel :pdf}
     :raporttiparametrit raporttiparametrit}
    liikennetapahtumat-sarakkeet
    (tiedot/jarjesta-tapahtumat tapahtumarivit)]])

(defn liikenne* [e! app valinnat]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do (e! (tiedot/->Nakymassa? true))
                           (tiedot/nakymaan e! valinnat))
                      #(e! (tiedot/->Nakymassa? false)))

    (fn [e! {:keys [valittu-liikennetapahtuma] :as app}]
      @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.
      (if-not valittu-liikennetapahtuma
        [liikennetapahtumataulukko e! app @kanavaurakka/kanavakohteet]
        [liikennetapahtumalomake e! app @kanavaurakka/kanavakohteet]))))

(defn liikennetapahtumat [e! app]
  [liikenne* e! app {:aikavali @u/valittu-aikavali}])

(defc liikenne []
  [tuck tiedot/tila liikennetapahtumat])

