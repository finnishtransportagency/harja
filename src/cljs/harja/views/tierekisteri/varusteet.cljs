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
            [clojure.string :as str]
            [reagent.core :refer [atom] :as r]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.debug :as debug]))

(defn oikeus-varusteiden-muokkaamiseen? []
  (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-varusteet (:id @nav/valittu-urakka)))

(defn varustehaku-ehdot [e! {haku? :haku-kaynnissa?
                             tr-osoite :tierekisteriosoite
                             varusteentunniste :tunniste
                             :as hakuehdot}]
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
                                         (str/blank? varusteentunniste)))
                      :ikoni (ikonit/livicon-search)}]
                    [yleiset/vihje "Hakua tehdessä käytetään joko tyyppiä ja tunnistetta, tai tyyppiä ja tr-osoitetta. Jos kaikki kolme on syötetty, käytetään haussa tyyppiä ja tunnistetta."]
                    (when haku?
                      [yleiset/ajax-loader "Varusteita haetaan tierekisteristä"])])
      :tunniste (comp :tunniste :varuste)
      :ei-borderia? false}
     [{:nimi :tietolaji
       :otsikko "Varusteen tyyppi"
       :tyyppi :valinta
       :pakollinen? true
       :valinnat (vec varusteet/tietolaji->selitys)
       :valinta-nayta #(if (nil? %) "- valitse -" (second %))
       :valinta-arvo first}
      (lomake/ryhma
        ""
        {:nimi :tierekisteriosoite
         :otsikko "Tierekisteriosoite"
         :tyyppi :tierekisteriosoite
         :sijainti (atom nil) ;; sijainti ei kiinnosta, mutta johtuen komponentin toiminnasta, atom täytyy antaa
         ;; FIXME: Jostain syystä tr-osoitteen pakollinen-merkki ei poistu, kun tunnisteen syöttää.
         ;:pakollinen? (str/blank? varusteentunniste)
         }
        {:nimi :tunniste
         :otsikko "Varusteen tunniste"
         :tyyppi :string
         ;:pakollinen? (not (tr-ok? tr-osoite))
         })]
     hakuehdot]))

(defn poista-varuste [e! tietolaji tunniste]
  (yleiset/varmista-kayttajalta
    {:otsikko "Varusteen poistaminen Tierekisteristä"
     :sisalto [:div "Haluatko varmasti poistaa tietolajin: "
               [:b (str (varusteet/tietolaji->selitys tietolaji) " (" tietolaji ")")] " varusteen, jonka tunniste on: "
               [:b tunniste] "."]
     :hyvaksy "Poista"
     :hyvaksy-ikoni (ikonit/livicon-trash)
     :hyvaksy-napin-luokka "nappi-kielteinen"
     :toiminto-fn (fn [] (e! (v/->PoistaVaruste tunniste)))}))

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
        tarkastus (assoc tarkastus :kuntoluokitus valittu-kuntoluokka
                                   :uusi-liite uusi-liite)]
    [modal/modal
     {:otsikko (str "Tarkasta varuste (tunniste: " tunniste ", tietolaji: " tietolaji ")")
      :nakyvissa? true
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
      [{:otsikko "Yleinen kuntoluokitus"
        :nimi :kuntoluokitus
        :tyyppi :valinta
        :pakollinen? true
        :valinnat (vec (keys kuntoluokka->selite))
        :fmt (fn [arvo] (if arvo (kuntoluokka->selite arvo) "- Valitse -"))
        :valinta-nayta (fn [arvo] (if arvo (kuntoluokka->selite arvo) "- Valitse -"))}
       {:otsikko "Lisätietoja"
        :nimi :lisatietoja
        :tyyppi :string}
       {:otsikko "Liitteet"
        :nimi :liitteet
        :tyyppi :komponentti
        :komponentti (fn [_]
                       [liitteet/liitteet (:id @nav/valittu-urakka) (:liitteet tarkastus)
                        {:uusi-liite-teksti "Lisää liite tarkastukseen"
                         :uusi-liite-atom (r/wrap (:uusi-liite tarkastus)
                                                  #(e! (v/->LisaaLiitetiedosto %)))
                         :modaalissa? true}])}]
      tarkastus]]))

(defn sarakkeet [e! tietolajin-listaus-skeema]
  (if oikeus-varusteiden-muokkaamiseen?
    (let [toiminnot {:nimi :toiminnot
                     :otsikko "Toiminnot"
                     :tyyppi :komponentti
                     :leveys 3.5
                     :komponentti (fn [{varuste :varuste}]
                                    (let [tunniste (:tunniste varuste)
                                          tietolaji (get-in varuste [:tietue :tietolaji :tunniste])]
                                      [:div
                                       [napit/tarkasta "Tarkasta" #(e! (v/->AloitaVarusteenTarkastus tunniste tietolaji))]
                                       [napit/muokkaa "Muokkaa" #(e! (v/->AloitaVarusteenMuokkaus tunniste))]
                                       [napit/poista "Poista" #(poista-varuste e! tietolaji tunniste)]]))}]
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
   [varustehaku-ehdot e! hakuehdot]

   (when tarkastus
     [varustetarkastuslomake e! tarkastus])

   (when (and listaus-skeema varusteet)
     [varustehaku-varusteet e! listaus-skeema varusteet tarkastus])])
