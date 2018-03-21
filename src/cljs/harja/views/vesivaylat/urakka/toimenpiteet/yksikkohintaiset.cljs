(ns harja.views.vesivaylat.urakka.toimenpiteet.yksikkohintaiset
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :refer [tuck]]
            [cljs-time.core :as time]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as tiedot]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.kokonaishintaiset :as kok-hint]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu-tiedot]
            [harja.views.vesivaylat.urakka.toimenpiteet.toimenpiteen-hinnoittelu :as hinnoittelu-ui]
            [harja.ui.komponentti :as komp]
            [harja.loki :as loki :refer [log]]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader ajax-loader-pieni]]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.leijuke :refer [leijuke]]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.views.vesivaylat.urakka.toimenpiteet.jaettu :as jaettu]
            [harja.ui.kentat :as kentat]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.hinta :as hinta]
            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.vesivaylat.tyo :as tyo]
            [harja.domain.toimenpidekoodi :as tpk]
            [harja.domain.sopimus :as sop]
            [harja.fmt :as fmt]
            [harja.ui.grid :as grid]
            [harja.ui.lomake :as lomake]
            [harja.ui.debug :as debug]
            [harja.domain.oikeudet :as oikeudet]
            [harja.views.kartta :as kartta]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.pvm :as pvm]
            [harja.id :refer [id-olemassa?]]
            [clojure.string :as str]
            [harja.ui.modal :as modal])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.tyokalut.ui :refer [for*]]))

;;;;;;;
;; Urakkatoiminnot: Hintaryhmän valitseminen

(defn- hinnoitteluvaihtoehdot [e! {:keys [valittu-hintaryhma toimenpiteet hintaryhmat] :as app}]
  [:div.inline-block {:style {:margin-right "10px"}}
   [yleiset/livi-pudotusvalikko
    {:valitse-fn #(e! (tiedot/->ValitseHintaryhma %))
     :format-fn #(or (::h/nimi %) "Valitse tilaus")
     :class "livi-alasveto-250"
     :valinta valittu-hintaryhma
     :disabled (not (jaettu-tiedot/joku-valittu? toimenpiteet))}
    hintaryhmat]])

(defn- siirra-hinnoitteluun-nappi [e! {:keys [toimenpiteet valittu-hintaryhma
                                              hintaryhmien-liittaminen-kaynnissa?
                                              toimenpiteiden-haku-kaynnissa?
                                              hintaryhmien-haku-kaynnissa?] :as app}]
  [napit/yleinen-ensisijainen
   (cond
     hintaryhmien-liittaminen-kaynnissa?
     [yleiset/ajax-loader-pieni "Liitetään.."]

     (or toimenpiteiden-haku-kaynnissa? hintaryhmien-haku-kaynnissa?)
     [yleiset/ajax-loader-pieni "Päivitetään.."]

     :default "Siirrä")
   #(e! (tiedot/->LiitaValitutHintaryhmaan
          valittu-hintaryhma
          (jaettu-tiedot/valitut-toimenpiteet toimenpiteet)))
   {:disabled (or (not (jaettu-tiedot/joku-valittu? toimenpiteet))
                  (not valittu-hintaryhma)
                  hintaryhmien-liittaminen-kaynnissa?
                  toimenpiteiden-haku-kaynnissa?
                  hintaryhmien-haku-kaynnissa?
                  (not (oikeudet/on-muu-oikeus? "siirrä-tilaukseen"
                                                oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                (:id @nav/valittu-urakka))))}])

(defn- hintaryhman-luonti [e! {:keys [hintaryhmat uuden-hintaryhman-lisays? uusi-hintaryhma
                                      hintaryhman-tallennus-kaynnissa?] :as app}]
  (if uuden-hintaryhman-lisays?
    [:span
     [:div.inline-block {:style {:margin-right "10px"}}
      [tee-kentta {:tyyppi :string
                   :placeholder "Tilauksen nimi"
                   :pituus-max 160}
       (r/wrap
         uusi-hintaryhma
         #(e! (tiedot/->UudenHintaryhmanNimeaPaivitetty %)))]]
     [napit/yleinen-ensisijainen
      (if hintaryhman-tallennus-kaynnissa? [yleiset/ajax-loader-pieni "Luodaan.."] "Luo")
      #(e! (tiedot/->LuoHintaryhma uusi-hintaryhma))
      {:disabled (or ;; Disabloidaan nappi jos nimi on jo olemassa, liittäminen menossa tai teksti puuttuu
                   ((set (map ::h/nimi hintaryhmat)) uusi-hintaryhma)
                   (empty? uusi-hintaryhma)
                   hintaryhman-tallennus-kaynnissa?)}]
     [napit/peruuta "Peruuta" #(e! (tiedot/->UudenHintaryhmanLisays? false))]]

    [napit/yleinen-ensisijainen
     "Luo uusi tilaus"
     #(e! (tiedot/->UudenHintaryhmanLisays? true))
     {:disabled (not (oikeudet/on-muu-oikeus? "tilausten-muokkaus"
                                              oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                              (:id @nav/valittu-urakka)))}]))

(defn- hinnoittelu [e! app]
  [:span
   [:span {:style {:margin-right "10px"}} "Siirrä valitut tilaukseen"]
   [hinnoitteluvaihtoehdot e! app]
   [siirra-hinnoitteluun-nappi e! app]
   [hintaryhman-luonti e! app]])

(defn- varmistusdialogi-sisalto [app]
  (let [valitut-toimenpiteet (filter :valittu? (:toimenpiteet app))]
    [:div
     (when (to/toimenpiteilla-hintaryhmia? valitut-toimenpiteet)
       (jaettu/varmistusdialog-ohje
         {:varmistusehto ::to/hintaryhma-id
          :valitut-toimenpiteet valitut-toimenpiteet
          :nayta-max 5
          :toimenpide-lisateksti-fn #(str "Tilaus: " (::h/nimi (h/hinnoittelu-idlla (:hintaryhmat app)
                                                                                    (::to/hintaryhma-id %))) ".")
          :varmistusteksti-header "Seuraavat toimenpiteet kuuluvat tilaukseen:"
          :varmistusteksti-footer "Nämä toimenpiteet irrotetaan tilauksesta siirron aikana."}))
     (when (to/toimenpiteilla-omia-hinnoitteluja? (filter :valittu? (:toimenpiteet app)))
       (jaettu/varmistusdialog-ohje
         {:varmistusehto ::to/oma-hinnoittelu
          :valitut-toimenpiteet valitut-toimenpiteet
          :nayta-max 5
          :toimenpide-lisateksti-fn #(str "Hinta: " (fmt/euro-opt (+ (hinta/kokonaishinta-yleiskustannuslisineen
                                                                       (get-in % [::to/oma-hinnoittelu ::h/hinnat]))
                                                                     (tyo/toiden-kokonaishinta
                                                                       (get-in % [::to/oma-hinnoittelu ::h/tyot])
                                                                       (tpk/aikavalin-hinnalliset-suunnitellut-tyot
                                                                         (:suunnitellut-tyot app)
                                                                         (get-in app [:valinnat :aikavali])))))
                                          ".")
          :varmistusteksti-header "Seuraavat toimenpiteet sisältävät hinnoittelutietoja:"
          :varmistusteksti-footer "Näiden toimenpiteiden hinnoittelutiedot poistetaan siirron aikana."}))
     [:p "Haluatko jatkaa?"]]))

(defn- valmistele-toimenpiteiden-siirto [app e!]
  (if (or (to/toimenpiteilla-hintaryhmia? (filter :valittu? (:toimenpiteet app)))
          (to/toimenpiteilla-omia-hinnoitteluja? (filter :valittu? (:toimenpiteet app))))
    (varmista-kayttajalta/varmista-kayttajalta
      {:otsikko "Siirto kokonaishintaisiin"
       :sisalto (varmistusdialogi-sisalto app)
       :hyvaksy "Siirrä kokonaishintaisiin"
       :toiminto-fn #(e! (tiedot/->SiirraValitutKokonaishintaisiin))})
    (e! (tiedot/->SiirraValitutKokonaishintaisiin))))

(defn luo-toimenpide-nappi [e! app]
  [napit/uusi "Luo toimenpide" #(e! (tiedot/->AvaaLomakkeelle tiedot/alustettu-toimenpide))])

(defn- urakkatoiminnot [e! app]
  [^{:key "luonti"}
  [luo-toimenpide-nappi e! app]

   ^{:key "siirto"}
  [jaettu/siirtonappi e! app
   "Siirrä kokonaishintaisiin"
   #(valmistele-toimenpiteiden-siirto app e!)
   #(oikeudet/on-muu-oikeus? "siirrä-kokonaishintaisiin"
                             oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                             (:id @nav/valittu-urakka))]
   ^{:key "hinnoittelu"}
   [hinnoittelu e! app]])

(defn laskutusdialogin-sisalto [teksti hinnoittelu laskutus-kk-atomi]
  [:div
   (str teksti)
   [:div.label-ja-alasveto.kuukausi
    [:span.alasvedon-otsikko "Kuukausi"]
    [yleiset/livi-pudotusvalikko
     {:valinta @laskutus-kk-atomi
      :format-fn (fn [alkupvm]
                   (if alkupvm
                     (let [kk-teksti (pvm/kuukauden-nimi (pvm/kuukausi alkupvm))]
                       (str (str/capitalize kk-teksti) " " (pvm/vuosi alkupvm)))
                     "- Valitse laskutuskuukausi - "))
      :valitse-fn (fn [arvo] (reset! laskutus-kk-atomi arvo))}
     (map
       first
       (pvm/aikavalin-kuukausivalit [(pvm/nyt) (time/plus (time/now) (time/months 8))]))]]])

(defn- laskutuslupadialogi [e! teksti hinnoittelu]
  (let [laskutus-kk-atomi (atom (or (::h/laskutus-pvm hinnoittelu) (time/plus (pvm/nyt) (time/months 1))))]
    (modal/nayta!
     {:otsikko "Muokkaa laskutuslupaa"
      :footer [:span
               [napit/takaisin "Peruuta" #(modal/piilota!)]
               [napit/peruuta "Hylkää"
                #(e! (tiedot/->MuutaHintaryhmanLaskutuslupaa (::h/id hinnoittelu) :hylatty nil))]
               [napit/hyvaksy "Hyväksy" #(e! (tiedot/->MuutaHintaryhmanLaskutuslupaa (::h/id hinnoittelu) :hyvaksytty @laskutus-kk-atomi))]]}
     [laskutusdialogin-sisalto teksti hinnoittelu laskutus-kk-atomi])))

(defn laskutuslupanappi [e! app hinnoittelu]
  (cond
    (nil? (::h/id hinnoittelu))
    nil

    (::h/laskutettu? hinnoittelu)
    (str "Laskutettu " (pvm/pvm (::h/laskutus-pvm hinnoittelu)))

    (::h/laskutus-pvm hinnoittelu)
    [:span
     (str "Laskutetaan " (pvm/pvm (::h/laskutus-pvm hinnoittelu)))
     [napit/yleinen-toissijainen
      ""
      #(laskutuslupadialogi e!
                            (str "Tilauksella " (::h/nimi hinnoittelu) " on laskutuslupa.")
                            hinnoittelu)
      {:ikoni (ikonit/save)
       :ikoninappi? true
       :disabled (:hintaryhman-laskutusluvan-tallennus-kaynnissa? app)}]]

    :else
    [napit/yleinen-ensisijainen
     "Laskutuslupa"
     #(laskutuslupadialogi e! "Anna tilaukselle laskutuslupa" hinnoittelu)
     {:ikoni (ikonit/save)
      :disabled (:hintaryhman-laskutusluvan-tallennus-kaynnissa? app)}]))

(defn- hintaryhman-hinnoittelu [e! app* hintaryhma]
  (let [hinnoittelu-id (get-in app* [:hinnoittele-hintaryhma ::h/id])
        hintaryhman-toimenpiteet (:toimenpiteet app*)
        valittu-aikavali (get-in app* [:valinnat :aikavali])
        suunnitellut-tyot (tpk/aikavalin-hinnalliset-suunnitellut-tyot (:suunnitellut-tyot app*)
                                                                       valittu-aikavali)
        hintaryhman-toimenpiteiden-omat-hinnat (remove nil? (mapcat #(get-in % [::to/oma-hinnoittelu ::h/hinnat])
                                                                    hintaryhman-toimenpiteet))
        hintaryhman-toimenpiteiden-omat-tyot (remove nil? (mapcat #(get-in % [::to/oma-hinnoittelu ::h/tyot])
                                                                  hintaryhman-toimenpiteet))
        hintaryhman-toimenpiteiden-yhteishinta (+ (hinta/kokonaishinta-yleiskustannuslisineen
                                                    hintaryhman-toimenpiteiden-omat-hinnat)
                                                  (tyo/toiden-kokonaishinta hintaryhman-toimenpiteiden-omat-tyot
                                                                            suunnitellut-tyot))
        hinnoitellaan? (and hinnoittelu-id (= hinnoittelu-id (::h/id hintaryhma)))
        hinnat (::h/hinnat hintaryhma)
        hintaryhman-kokonaishinta (hinta/kokonaishinta-yleiskustannuslisineen hinnat)]
    [:div.vv-hintaryhman-hinnoittelu-wrapper
     [:div.vv-hintaryhman-hinnoittelu
      (if hinnoitellaan?
        [:div
         [:div.inline-block {:style {:margin-right "10px"}}
          [tee-kentta {:tyyppi :numero
                       :placeholder "Syötä hinta"
                       :kokonaisosan-maara 7}
           (r/wrap (hinta/hinnan-summa-otsikolla
                     (get-in app* [:hinnoittele-hintaryhma ::h/hinnat])
                     tiedot/hintaryhman-hintakentta-otsikko)
                   #(e! (tiedot/->AsetaHintaryhmakentalleTiedot
                          {::hinta/otsikko tiedot/hintaryhman-hintakentta-otsikko
                           ::hinta/summa %})))]
          [:span " "]
          [:span "€"]]
         [napit/tallenna
          "Valmis"
          #(e! (tiedot/->TallennaHintaryhmanHinnoittelu (:hinnoittele-hintaryhma app*)))
          {:disabled (or (:hintaryhman-hinnoittelun-tallennus-kaynnissa? app*)
                         (::h/laskutettu? hintaryhma)
                         (not (oikeudet/on-muu-oikeus? "hinnoittele-tilaus"
                                                       oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                       (:id @nav/valittu-urakka))))}]
         [napit/peruuta
          "Peruuta"
          #(e! (tiedot/->PeruHintaryhmanHinnoittelu))]]

        (when-not (tiedot/valiaikainen-hintaryhma? hintaryhma)
          (if (and (nil? hintaryhman-toimenpiteiden-yhteishinta)
                   (nil? hintaryhman-kokonaishinta)
                   (empty? hinnat))
            [napit/yleinen-ensisijainen
             "Määrittele yksi hinta koko tilaukselle"
             #(e! (tiedot/->AloitaHintaryhmanHinnoittelu (::h/id hintaryhma)))
             {:disabled (or (:hintaryhman-hinnoittelun-tallennus-kaynnissa? app*)
                            (::h/laskutettu? hintaryhma))}]
            [:div
             [:div.inline-block {:style {:margin-right "10px"}}
              (if (zero? hintaryhman-toimenpiteiden-yhteishinta)
                [:span
                 [:b "Tilauksen hinta: "] [:span (fmt/euro-opt (hinta/kokonaishinta-yleiskustannuslisineen hinnat))]]
                ;; Yleensä hintaryhmän toimenpiteillä on vain yksi könttähinta.
                ;; On kuitenkin mahdollista määrittää myös toimenpiteille omia hintoja hintaryhmän sisällä
                ;; Näytetään tällöin ryhmän hinta, toimenpiteiden kok. hinta ja yhteissumma
                [yleiset/tietoja {:tietokentan-leveys "180px"}
                 "Toimenpiteet:" (fmt/euro-opt hintaryhman-toimenpiteiden-yhteishinta)
                 "Tilauksen hinta:" (fmt/euro-opt hintaryhman-kokonaishinta)
                 "Yhteensä:" (fmt/euro-opt (+ hintaryhman-toimenpiteiden-yhteishinta hintaryhman-kokonaishinta))])]
             [:div.inline-block {:style {:vertical-align :top}}
              [napit/yleinen-toissijainen
               (ikonit/muokkaa)
               #(e! (tiedot/->AloitaHintaryhmanHinnoittelu (::h/id hintaryhma)))
               {:ikoninappi? true
                :disabled (not (oikeudet/on-muu-oikeus? "hinnoittele-tilaus"
                                                        oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                        (:id @nav/valittu-urakka)))}]]
             [laskutuslupanappi e! app* hintaryhma]])))]]))

(defn- toimenpidelomake [e! {:keys [valittu-toimenpide tallennus-kaynnissa?] :as app}]
  (let [uusi-toimenpide? (not (id-olemassa? (::to/id valittu-toimenpide)))]
    [:div
     [napit/takaisin #(e! (tiedot/->AvaaLomakkeelle nil))]
     [lomake/lomake
      {:otsikko (if uusi-toimenpide?
                  "Kirjaa turvalaitteeton toimenpide"
                  "Muokkaa toimenpidettä")
       :muokkaa! #(e! (tiedot/->ToimenpidettaMuokattu (lomake/ilman-lomaketietoja %)))
       :footer-fn (fn [toimenpide]
                    [:div
                     [napit/tallenna
                      "Tallenna toimenpide"
                      #(e! (tiedot/->TallennaToimenpide (lomake/ilman-lomaketietoja toimenpide)))
                      {:disabled (or tallennus-kaynnissa?
                                     (not (lomake/voi-tallentaa? toimenpide)))}]])}
      [{:otsikko "Aika"
        :tyyppi :pvm-aika
        :pakollinen? true
        :nimi ::to/suoritettu
        :validoi [[:ei-tyhja "Anna aika"]]}
       {:otsikko "Sopimus"
        :nimi ::to/sopimus
        :tyyppi :valinta
        :pakollinen? true
        :valinta-nayta ::sop/nimi
        :valinnat (map (fn [[id nimi]] {::sop/id id ::sop/nimi nimi}) (:sopimukset @nav/valittu-urakka))
        :fmt ::sop/nimi
        :validoi [[:ei-tyhja "Valitse sopimus"]]}
       {:otsikko "Työlaji"
        :tyyppi :valinta
        :nimi ::to/tyolaji
        :valinnat (into [nil] (to/jarjesta-reimari-tyolajit (set (mapv val to/reimari-tyolajit))))
        :valinta-nayta #(or (to/reimari-tyolaji-fmt %) "Ei työlajia")
        :fmt #(or (to/reimari-tyolaji-fmt %) "Ei työlajia")}
       (when (or (::to/tyolaji valittu-toimenpide)
                 (::to/tyoluokka valittu-toimenpide))
         {:otsikko "Työluokka"
          :tyyppi :valinta
          :nimi ::to/tyoluokka
          :valinnat (into [nil] (to/jarjesta-reimari-tyoluokat(set (mapv val to/reimari-tyoluokat))))
          :valinta-nayta #(or (to/reimari-tyoluokka-fmt %) "Ei työluokkaa")
          :fmt #(or (to/reimari-tyoluokka-fmt %) "Ei työluokkaa")})
       (when (or (and (::to/tyolaji valittu-toimenpide)
                      (::to/tyoluokka valittu-toimenpide))
                 (::to/toimenpide valittu-toimenpide))
         {:otsikko "Toimenpide"
          :tyyppi :valinta
          :nimi ::to/toimenpide
          :valinnat (into [nil] (to/jarjesta-reimari-toimenpidetyypit (set (mapv val to/reimari-toimenpidetyypit))))
          :valinta-nayta #(or (to/reimari-toimenpidetyyppi-fmt %) "Ei toimenpidettä")
          :fmt #(or (to/reimari-toimenpidetyyppi-fmt %) "Ei toimenpidettä")})
       {:otsikko "Lisätieto"
        :tyyppi :text
        :nimi ::to/lisatieto}]
      valittu-toimenpide]]))

(defn- yksikkohintaiset-toimenpiteet-nakyma [e! app ulkoiset-valinnat]
  (komp/luo
    (komp/watcher tiedot/valinnat (fn [_ _ uusi]
                                    (e! (tiedot/->PaivitaValinnat uusi))))
    (komp/sisaan-ulos #(do
                         (e! (tiedot/->Nakymassa? true))
                         (e! (tiedot/->PaivitaValinnat {:urakka-id (get-in ulkoiset-valinnat [:urakka :id])
                                                        :sopimus-id (first (:sopimus ulkoiset-valinnat))
                                                        :aikavali (:aikavali ulkoiset-valinnat)}))
                         (e! (tiedot/->HaeHintaryhmat))
                         (e! (tiedot/->HaeSuunnitellutTyot)))
                      #(do
                         (u/valitse-oletussopimus-jos-valittuna-kaikki!)
                         (e! (tiedot/->Nakymassa? false))
                         (e! (tiedot/->TyhjennaSuunnitellutTyot))))
    (fn [e! {:keys [toimenpiteet toimenpiteiden-haku-kaynnissa? hintaryhmat valittu-toimenpide] :as app}]
      @tiedot/valinnat ;; Reaktio on pakko lukea komponentissa, muuten se ei päivity.

      (if (some? valittu-toimenpide)
        ;; Tänne ei tarvita esim kartan paikkaa, koska tällä hetkellä lomakkeelle avattavilla
        ;; toimenpiteillä ei ikinä ole sijaintitietoja, koska ne eivät liity turvalaitteseen
        [:div
         [debug/debug app]
         [toimenpidelomake e! app]]

        (let [hintaryhmat (concat
                           [tiedot/kokonaishintaisista-siirretyt-hintaryhma]
                           [tiedot/reimarin-lisatyot-hintaryhma]
                           (h/jarjesta-hintaryhmat hintaryhmat))]
         [:div
          [kartta/kartan-paikka]
          [debug/debug app]
          [jaettu/suodattimet e! tiedot/->PaivitaValinnat app (:urakka ulkoiset-valinnat)
           tiedot/vaylahaku tiedot/turvalaitehaku
           {:urakkatoiminnot (urakkatoiminnot e! app)}]

          [jaettu/tulokset e! app
           [:div
            (for* [hintaryhma hintaryhmat
                   :let [hintaryhma-id (::h/id hintaryhma)
                         hintaryhman-toimenpiteet (to/toimenpiteet-hintaryhmalla toimenpiteet hintaryhma-id)
                         app* (assoc app :toimenpiteet hintaryhman-toimenpiteet)
                         listaus-tunniste (keyword (str "listaus-" hintaryhma-id))
                         hintaryhma-tyhja? (::h/tyhja? hintaryhma) ;; Ei sisällä toimenpiteitä kannassa
                         nayta-hintaryhma?
                         (boolean
                           (or
                             ;; Kok. hint. siirretyt tai reimarin lisätyöt -ryhmä, jos ei tyhjä
                             (and (tiedot/valiaikainen-hintaryhma? hintaryhma)
                                  (not (empty? hintaryhman-toimenpiteet)))
                             hintaryhma-tyhja? ;; Kannassa täysin tyhjä hintaryhmä; piirretään aina, jotta voi poistaa
                             (not (empty? hintaryhman-toimenpiteet)))) ;; Sis. toimenpiteitä käytetyillä suodattimilla
                         nayta-hintaryhman-yhteenveto? (boolean (and hintaryhma-id
                                                                     (not (empty? hintaryhman-toimenpiteet))))]]

              (when nayta-hintaryhma?
                ^{:key (str "yksikkohintaiset-toimenpiteet-" hintaryhma-id "-hintaryhma")}
                [:div.vv-toimenpideryhma
                 ^{:key (str "yksikkohintaiset-toimenpiteet-" hintaryhma-id "-otsikko")}
                 (if toimenpiteiden-haku-kaynnissa?
                   [:h1 [ajax-loader "Päivitetään listaa.." {:sama-rivi? true}]]
                   [:span
                   [jaettu/hintaryhman-otsikko
                    (h/hintaryhman-nimi hintaryhma)
                    (str " (" (count hintaryhman-toimenpiteet) " kpl)")]
                   [napit/nappi
                    (ikonit/map-marker)
                    #(if (tiedot/hintaryhma-korostettu? hintaryhma app)
                       (e! (tiedot/->PoistaHintaryhmanKorostus))

                       (e! (tiedot/->KorostaHintaryhmaKartalla hintaryhma)))
                    {:ikoninappi? true
                     :disabled hintaryhma-tyhja?
                     :luokka (str "vv-hintaryhma-korostus-nappi "
                                  (if (tiedot/hintaryhma-korostettu? hintaryhma app)
                                    "nappi-ensisijainen"
                                    "nappi-toissijainen"))}]])

                 (if hintaryhma-tyhja?
                   ^{:key (str "yksikkohintaiset-toimenpiteet-" hintaryhma-id "-top-level")}
                   [:div
                    ^{:key (str "yksikkohintaiset-toimenpiteet-" hintaryhma-id "-ohje")}
                    [:p "Ei toimenpiteitä - Lisää tilaukseen toimenpiteitä valitsemalla haluamasi toimenpiteet ja valitsemalla yltä toiminto \"Siirrä valitut tilaukseen\"."]
                    ^{:key (str "yksikkohintaiset-toimenpiteet-" hintaryhma-id "-poistonappi")}
                    [napit/poista "Poista tyhjä tilaus" #(e! (tiedot/->PoistaHintaryhmat #{hintaryhma-id}))
                     {:disabled (or (:hintaryhmien-poisto-kaynnissa? app)
                                    (not (oikeudet/on-muu-oikeus? "tilausten-muokkaus"
                                                                  oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                                  (:id @nav/valittu-urakka))))}]]
                   ^{:key (str "yksikkohintaiset-toimenpiteet-" hintaryhma-id)}

                   [jaettu/listaus e! app*
                    {:sarakkeet
                     [jaettu/sarake-tyolaji
                      jaettu/sarake-tyoluokka
                      jaettu/sarake-toimenpide
                      jaettu/sarake-pvm
                      jaettu/sarake-vayla
                      jaettu/sarake-turvalaite
                      jaettu/sarake-turvalaitenumero
                      jaettu/sarake-komponentit
                      jaettu/sarake-vikakorjaus
                      jaettu/sarake-lisatieto
                      (jaettu/sarake-liitteet e! app #(oikeudet/on-muu-oikeus?
                                                        "lisää-liite"
                                                        oikeudet/urakat-vesivaylatoimenpiteet-yksikkohintaiset
                                                        (:id @nav/valittu-urakka)))
                      {:otsikko "Hinta" :tyyppi :komponentti :leveys 8
                       :komponentti (fn [rivi]
                                      [hinnoittelu-ui/hinnoittele-toimenpide e! app* rivi listaus-tunniste])}
                      {:otsikko "Laskutuslupa"
                       :tyyppi :komponentti
                       :leveys 8
                       :komponentti (fn [rivi]
                                      [laskutuslupanappi e! app* (::to/oma-hinnoittelu rivi)])}
                      (jaettu/sarake-checkbox e! app*)]
                     :listaus-tunniste listaus-tunniste
                     :avaa-toimenpide-lomakkeelle #(e! (tiedot/->AvaaLomakkeelle %))
                     :rivi-klikattu [tiedot/poista-hintaryhmien-korostus]
                     :infolaatikon-tila-muuttui [tiedot/poista-hintaryhmien-korostus]
                     :footer (when nayta-hintaryhman-yhteenveto?
                               [hintaryhman-hinnoittelu e! app* hintaryhma])
                     :paneelin-checkbox-sijainti "95.5%"
                     :vaylan-checkbox-sijainti "95.5%"}])]))]]])))))

(defn- yksikkohintaiset-toimenpiteet* [e! app]
  [yksikkohintaiset-toimenpiteet-nakyma e! app {:urakka @nav/valittu-urakka
                                                :sopimus @u/valittu-sopimusnumero
                                                :aikavali @u/valittu-aikavali}])

(defn yksikkohintaiset-toimenpiteet []
  [tuck (jaettu-tiedot/yhdista-tilat! tiedot/tila kok-hint/tila) yksikkohintaiset-toimenpiteet*])
