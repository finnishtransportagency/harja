(ns harja.views.tierekisteri.varusteet
  "Tierekisterin varusteiden käsittelyyn käyttöliittymä"
  (:require [harja.tiedot.tierekisteri.varusteet :as v]
            [harja.domain.tierekisteri.varusteet :as varusteet]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.loki :refer [log]]
            [harja.ui.debug :refer [debug]]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.modal :as modal]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [clojure.string :as str]
            [reagent.core :refer [atom] :as r]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.debug :as debug]
            [harja.tiedot.istunto :as istunto]))

(defn oikeus-varusteiden-muokkaamiseen? []
  (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-varusteet (:id @nav/valittu-urakka)))

(defn varustehaku-ehdot [e! {haku? :haku-kaynnissa?
                             tr-osoite :tierekisteriosoite
                             varusteentunniste :tunniste
                             varusteiden-haun-tila :varusteiden-haun-tila
                             :as hakuehdot}
                         varusteita-haettu?]
  (let [tr-ok? (fn [{:keys [numero alkuosa alkuetaisyys loppuosa loppuetaisyys]}]
                 (and numero alkuosa alkuetaisyys loppuosa loppuetaisyys))]
    [lomake/lomake
     {:otsikko "Hae varusteita Tierekisteristä"
      :muokkaa! #(e! (v/->AsetaVarusteidenHakuehdot %))
      :footer-fn (fn [rivi]
                   [:div
                    [napit/yleinen-toissijainen "Hae Tierekisteristä"
                     #(e! (v/->HaeVarusteita))
                     {:disabled (or (:haku-kaynnissa? hakuehdot)
                                    (and (not (tr-ok? tr-osoite))
                                         (str/blank? varusteentunniste))
                                    (not (istunto/ominaisuus-kaytossa? :tierekisteri)))
                      :ikoni (ikonit/livicon-search)}]
                    [napit/poista "Tyhjennä hakutulokset"
                     #(e! (v/->TyhjennaHakutulokset))
                     {:disabled (not varusteita-haettu?)}]
                    [yleiset/vihje "Hakua tehdessä käytetään joko tyyppiä ja tunnistetta, tai tyyppiä ja tr-osoitetta. Jos kaikki kolme on syötetty, käytetään haussa tyyppiä ja tunnistetta."]
                    (when haku?
                      [yleiset/ajax-loader "Varusteita haetaan tierekisteristä"])])
      :tunniste (comp :tunniste :varuste)
      :ei-borderia? false}
     [{:nimi :tietolaji
       :otsikko "Varusteen tyyppi"
       :tyyppi :valinta
       :pakollinen? true
       :valinnat (sort-by second (vec varusteet/tietolaji->selitys))
       :valinta-nayta #(if (nil? %) "- valitse -" (second %))
       :valinta-arvo first}
      {:nimi :voimassaolopvm
       :otsikko "Voimassaolopäivämäärä"
       :tyyppi :pvm}
      {:nimi :varusteiden-haun-tila
       :otsikko "Hae"
       :uusi-rivi? true
       :tyyppi :radio-group
       :vaihtoehdot [:sijainnilla :tunnisteella]
       :vaihtoehto-nayta (fn [arvo]
                           ({:sijainnilla "Sijainnilla"
                             :tunnisteella "Tunnisteella"}
                             arvo))}
      (when (= :sijainnilla varusteiden-haun-tila)
        {:nimi :tierekisteriosoite
         :uusi-rivi? true
         :otsikko "Tierekisteriosoite"
         :tyyppi :tierekisteriosoite
         :sijainti (atom nil) ;; sijainti ei kiinnosta, mutta johtuen komponentin toiminnasta, atom täytyy antaa
         :pakollinen? (= :sijainnilla varusteiden-haun-tila)})
      (when (= :tunnisteella varusteiden-haun-tila)
        {:nimi :tunniste
         :otsikko "Varusteen tunniste"
         :uusi-rivi? true
         :tyyppi :string
         :pakollinen? (= :tunnisteella varusteiden-haun-tila)})]
     hakuehdot]))

(defn poista-varuste [e! tietolaji tunniste tie]
  (varmista-kayttajalta/varmista-kayttajalta
    {:otsikko "Varusteen poistaminen Tierekisteristä"
     :sisalto [:div "Haluatko varmasti poistaa tietolajin: "
               [:b (str (varusteet/tietolaji->selitys tietolaji) " (" tietolaji ")")] " varusteen, jonka tunniste on: "
               [:b tunniste] "."]
     :hyvaksy "Poista"
     :toiminto-fn (fn [] (e! (v/->PoistaVaruste tunniste tie)))}))

(def kuntoluokka->selite {"1" "Ala-arvoinen"
                          "2" "Merkittäviä puutteita"
                          "3" "Epäoleellisia puutteita"
                          "4" "Hyvä"
                          "5" "Erinomainen"})

(defn varustetarkastuslomake [e! {tietolaji :tietolaji
                                  tunniste :tunniste
                                  varuste :varuste
                                  tarkastus :tiedot
                                  uusi-liite :uusi-liite}]
  (let [varusteen-kuntoluokka (get-in varuste [:tietue :tietolaji :arvot "kuntoluokitus"])
        valittu-kuntoluokka (cond
                              (and (nil? tarkastus) (str/blank? varusteen-kuntoluokka)) (ffirst kuntoluokka->selite)
                              (nil? tarkastus) varusteen-kuntoluokka
                              :default (:kuntoluokitus tarkastus))
        tarkastus (assoc tarkastus
                    ;; Palautunut kuntoluokitus on nil, jos tietolajiin ei kuulu sellaista tietoa. Pidetään tieto nillinä.
                    ;; Jos kuntoluokitus kuuluu tietolajiin, mutta sitä ei ole tehty, kuntluokitus on tyhjä. Korvataan uudella tiedolla.
                    :kuntoluokitus (if (= varusteen-kuntoluokka nil) nil valittu-kuntoluokka)
                    :uusi-liite uusi-liite)]
    [modal/modal
     {:otsikko (str "Tarkasta varuste (tunniste: " tunniste ", tietolaji: " tietolaji "). "
                    (if (= varusteen-kuntoluokka nil) "Varustetyyppi ei vaadi kuntoluokkaa. Kirjaa korjauskohteet lisätietoihin."))
      :nakyvissa? true
      :sulje-fn #(e! (v/->PeruutaVarusteenTarkastus))
      :footer [:span
               [:button.nappi-toissijainen {:type "button"
                                            :on-click #(e! (v/->PeruutaVarusteenTarkastus))}
                [:div (ikonit/livicon-ban) " Peruuta"]]
               [:button.nappi-myonteinen {:type "button" :on-click
                                          #(e! (v/->TallennaVarustetarkastus varuste tarkastus))}
                [:div (ikonit/livicon-save) " Tallenna"]]]}
     [lomake/lomake
      {:ei-borderia? true
       :muokkaa! #(e! (v/->AsetaVarusteTarkastuksenTiedot %))}
      [(if (not= varusteen-kuntoluokka nil) ;; Näytetään kuntoluokkavalinta vain, kun kuntoluokka kuuluu tietolajiin
         {:otsikko "Yleinen kuntoluokitus"
          :nimi :kuntoluokitus
          :tyyppi :valinta
          :pakollinen? true
          :valinnat (vec (keys kuntoluokka->selite))
          :fmt (fn [arvo] (if arvo (kuntoluokka->selite arvo) "- Valitse -"))
          :valinta-nayta (fn [arvo] (if arvo (kuntoluokka->selite arvo) "- Valitse -"))})
       {:otsikko "Lisätietoja"
        :nimi :lisatietoja
        :tyyppi :string}
       {:otsikko "Liitteet"
        :nimi :liitteet
        :tyyppi :komponentti
        :komponentti (fn [_]
                       [liitteet/liitteet-ja-lisays (:id @nav/valittu-urakka) (:liitteet tarkastus)
                        {:uusi-liite-teksti "Lisää liite tarkastukseen"
                         :uusi-liite-atom (r/wrap (:uusi-liite tarkastus)
                                                  #(e! (v/->LisaaLiitetiedosto %)))
                         :salli-poistaa-lisatty-liite? true
                         :poista-lisatty-liite-fn #(e! (v/->PoistaUusiLiitetiedosto %))
                         :modaalissa? true}])}]
      tarkastus]]))

(defn sarakkeet [e! tietolajin-listaus-skeema]
  (if (oikeus-varusteiden-muokkaamiseen?)
    (let [toiminnot {:nimi :toiminnot
                     :otsikko "Toiminnot"
                     :tyyppi :komponentti
                     :leveys 3.5
                     :komponentti (fn [{varuste :varuste}]
                                    (let [tunniste (:tunniste varuste)
                                          tie (get-in varuste [:tietue :sijainti :tie])
                                          tietolaji (get-in varuste [:tietue :tietolaji :tunniste])]
                                      (if (varusteet/muokkaaminen-sallittu? tietolaji)
                                        [:div
                                         (when (varusteet/tarkastaminen-sallittu? tietolaji) [napit/tarkasta "Tarkasta" #(e! (v/->AloitaVarusteenTarkastus tunniste tietolaji tie))
                                                                                              {:disabled (not (istunto/ominaisuus-kaytossa? :tierekisteri))}])
                                         [napit/muokkaa "Muokkaa" #(e! (v/->AloitaVarusteenMuokkaus tunniste tie)) {:disabled (not (istunto/ominaisuus-kaytossa? :tierekisteri))}]
                                         [napit/poista "Poista" #(poista-varuste e! tietolaji tunniste tie) {:disabled (not (istunto/ominaisuus-kaytossa? :tierekisteri))}]]
                                        [:div
                                         [napit/avaa "Avaa" #(e! (v/->AvaaVaruste tunniste tie))]])))}]
      (conj tietolajin-listaus-skeema toiminnot))
    tietolajin-listaus-skeema))

(defn varustehaku-varusteet [e! tietolajin-listaus-skeema varusteet]
  [grid/grid
   {:otsikko "Tierekisteristä löytyneet varusteet"
    :tunniste (fn [varuste]
                ;; Valitettavasti varusteiden tunnisteet eivät ole uniikkeja, vaan
                ;; sama varuste voi olla pätkitty useiksi TR osoitteiksi, joten yhdistetään
                ;; niiden avaimeksi tunniste ja osoite.
                (str (get-in varuste [:varuste :tunniste])
                     "_" (pr-str (get-in varuste [:varuste :tietue :sijainti :tie]))))}
   (sarakkeet e! tietolajin-listaus-skeema)
   varusteet])

(defn varustehaku
  "Komponentti, joka näyttää lomakkeen varusteiden hakemiseksi tierekisteristä
  sekä haun tulokset."
  [e! {{:keys [hakuehdot listaus-skeema varusteet tarkastus]} :tierekisterin-varusteet :as app}]
  [:div.varustehaku
   [varustehaku-ehdot e! hakuehdot (not (empty? varusteet))]

   (when tarkastus
     [varustetarkastuslomake e! tarkastus])

   (when (and listaus-skeema varusteet)
     [varustehaku-varusteet e! listaus-skeema varusteet])])
