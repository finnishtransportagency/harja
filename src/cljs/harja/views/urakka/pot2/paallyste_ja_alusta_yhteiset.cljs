(ns harja.views.urakka.pot2.paallyste-ja-alusta-yhteiset
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.napit :as napit]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
            [harja.domain.tierekisteri :as tr]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
            [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.ui.yleiset :as yleiset]
            [harja.views.urakka.pot-yhteinen :as pot-yhteinen]
            [harja.ui.viesti :as viesti]
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn validoi-kaistavalinta
  [rivi taulukko]
  (let [{:keys [kaistat]} (:paallystysilmoitus-lomakedata @paallystys/tila)
        valittu-kaista (get-in rivi [:tr-kaista])
        alikohde (select-keys rivi tr/vali-avaimet)
        ajorata (get-in alikohde [:tr-ajorata])
        alikohteen-kaistat (get-in kaistat [(select-keys alikohde tr/paaluvali-avaimet) ajorata])
        validi? (some #(= valittu-kaista (:kaista %)) alikohteen-kaistat)]

    #_#_#_#_
    (println "### alikohde:\n" (with-out-str
                             (cljs.pprint/pprint alikohde)))
    (println "### Valittu kaista: " valittu-kaista " ajorata: " ajorata)
    (println "### kaistat:\n" (with-out-str
                             (cljs.pprint/pprint kaistat)))
    (println "### alikohteen kaistat\n" (with-out-str
                                          (cljs.pprint/pprint alikohteen-kaistat)))

    (when-not validi?
      {:tr-kaista
       ["Kaista-aineiston mukaan tällä tieosoitteella ei ole kyseistä kaistaa. Tarkista tieosoitteen ja kaistatiedon oikeellisuus."]})))

;; ----

(def hint-kopioi-kaistoille "Kopioi rivin sisältö kaikille rinnakkaisille kaistoille. Jos kaistaa ei vielä ole, se lisätään taulukkoon.")
(def hint-kopioi-kaistoille-lyhyt "Kopioi rinnakkaisille kaistoille")
(def hint-kopioi-toiselle-ajr "Kopioi toisen ajoradan vastaavalle kaistalle")
(def hint-nayta-virheet "Lähetys epäonnistunut, näytä lisää")

;; Tärkeää käytettävyyden kannalta, että kulutuskerroksen ja alustan sarakkeet ovat kohdikkain
;; siksi huomioitava tämä jos sarakkeita lisätään tai poistetaan jompaan kumpaan
(def gridin-leveydet
  {:toimenpide 3
   :perusleveys 2
   :materiaali 3
   :tp-tiedot 8
   :toiminnot 3})

(def undo-aikaikkuna-ms 10000)

(def undo-tiedot (atom nil))
(def edellinen-tila (atom nil))

(defn poista-undo-tiedot []
  (when (:timeout-id @undo-tiedot)
    (.clearTimeout js/window (:timeout-id @undo-tiedot)))
  (reset! undo-tiedot nil))

(defn tarjoa-toiminnon-undo [vanha-tieto tyyppi index]
  (poista-undo-tiedot)
  (reset! edellinen-tila vanha-tieto)
  (let [timeout-id (yleiset/fn-viiveella poista-undo-tiedot undo-aikaikkuna-ms)]
    (reset! undo-tiedot {:tyyppi tyyppi :index index :timeout-id timeout-id})))

(defn lahetys-virheet-nappi [rivi muoto]
  (let [nayta-virheet-fn (fn [{:keys [velho-lahetyksen-aika velho-lahetyksen-vastaus] :as rivi}]
                           (varmista-kayttajalta/varmista-kayttajalta
                             {:otsikko "YHA-lähetyksessä virhe"
                              :sisalto (pot-yhteinen/lahetys-virhe-teksti rivi)
                              :hyvaksy "OK"
                              :toiminto-fn (constantly nil)
                              :napit [:hyvaksy]}))]
    [yleiset/wrap-if true
     [yleiset/tooltip {} :% hint-nayta-virheet]
     [napit/nappi
      [:span
       (ikonit/alert-svg 14)
       ;       [ikonit/livicon-warning-sign {:class "red-dark"}]
       (when (= muoto :pitka)
         [:span
          [:span {:class "black-lighter"} " Lähetyksessä virheitä "]
          (ikonit/nelio-info 14)])]
      #(nayta-virheet-fn rivi)
      {:disabled? false
       :luokka "napiton-nappi"
       :toiminto-args [rivi]}]]))

(defn rivin-toiminnot [e! ohjauskahva nappi-disabled? rivi rivit-atom sort-atom tyyppi index]
  (let [kohdeosat-muokkaa! (fn [uudet-kohdeosat-fn index]
                             (let [vanhat-kohdeosat @rivit-atom
                                   uudet-kohdeosat (uudet-kohdeosat-fn vanhat-kohdeosat)]
                               (e! (pot2-tiedot/->Pot2Muokattu))
                               (tarjoa-toiminnon-undo vanhat-kohdeosat tyyppi index)
                               (swap! rivit-atom (fn [_]
                                                   uudet-kohdeosat))))
        pilko-osa-fn (fn [index tyyppi]
                       (kohdeosat-muokkaa! (fn [vanhat-kohdeosat]
                                             (if (= tyyppi :paallystekerros)
                                               (yllapitokohteet/pilko-paallystekohdeosa vanhat-kohdeosat (inc index) {})
                                               (yllapitokohteet/lisaa-uusi-pot2-alustarivi vanhat-kohdeosat (inc index) {})))
                         index))
        poista-osa-fn (fn [index ohjauskahva]
                        (kohdeosat-muokkaa! (fn [vanhat-kohdeosat]
                                              (yllapitokohteet/poista-kohdeosa vanhat-kohdeosat (inc index)))
                          ;; Jos poistetaan ylin rivi (index 0), lisätään yksi, jotta undo tarjotaan riville 1
                          (if (= index (- (count (keys @rivit-atom)) 1))
                            ;; Jos poistetaan alin rivi, vähennetään indeksiä jotta undo ilmestyy edeltävälle riville (muuten ei näkyisi ollenkaan)
                            (dec index)
                            index))
                        (when ohjauskahva (grid/validoi-grid ohjauskahva)))
        ]
    [{:ikoni (ikonit/copy-lane-svg)
      :tyyppi :kopioi-kaista
      :disabled? nappi-disabled?
      :hover-txt hint-kopioi-kaistoille
      :teksti hint-kopioi-kaistoille-lyhyt
      :toiminto #(do
                   (tarjoa-toiminnon-undo @rivit-atom tyyppi index)
                   (e! (pot2-tiedot/->KopioiToimenpiteetTaulukossaKaistoille rivi rivit-atom sort-atom))
                   (when ohjauskahva (grid/validoi-grid ohjauskahva)))
      :toiminto-args [rivi rivit-atom]}
     {:ikoni (ikonit/action-copy)
      :tyyppi :kopioi
      :disabled? (or nappi-disabled?
                   (not (#{1 2} (:tr-ajorata rivi))))
      :hover-txt hint-kopioi-toiselle-ajr
      :toiminto #(do
                   (tarjoa-toiminnon-undo @rivit-atom tyyppi index)
                   (e! (pot2-tiedot/->KopioiToimenpiteetTaulukossaAjoradoille rivi rivit-atom sort-atom))
                   (when ohjauskahva (grid/validoi-grid ohjauskahva)))}
     {:ikoni (ikonit/road-split)
      :tyyppi :pilko
      :disabled? nappi-disabled?
      :hover-txt yllapitokohteet/hint-pilko-osoitevali
      :toiminto pilko-osa-fn
      :toiminto-args [index tyyppi]}
     {:ikoni (ikonit/action-delete)
      :tyyppi :poista
      :disabled? nappi-disabled?
      :hover-txt yllapitokohteet/hint-poista-rivi
      :toiminto poista-osa-fn
      :toiminto-args [index ohjauskahva]}]))

(defn rivin-lisatoiminnot-dropdown [_rivin-toiminnot lisatoiminnot-auki?]
  (komp/luo
    (komp/klikattu-ulkopuolelle #(reset! lisatoiminnot-auki? false))
    (fn [rivin-toiminnot _]
      [:div.aina-viimeinen
       [napit/nappi-hover-vihjeella
        {:ikoni (ikonit/navigation-more)
         :disabled? false
         :hover-txt "Lisää toimintoja"
         :luokka "napiton-nappi btn-xs"
         :wrapper-luokka "aina-viimeinen"
         :toiminto #(swap! lisatoiminnot-auki? not)}]
       [:ul.lisatoiminnot
        {:class (when @lisatoiminnot-auki? "auki")}
        (for*
          [{:keys [toiminto toiminto-args ikoni] :as rivin-toiminto} rivin-toiminnot]
          [:li
           [napit/yleinen-ensisijainen
            (or (:teksti rivin-toiminto) (:hover-txt rivin-toiminto))
            toiminto
            {:toiminto-args toiminto-args
             :ikoni ikoni
             :luokka "napiton-nappi btn-xs"}]])]])))

(defn rivin-toiminnot-sarake
  [rivi osa e! app kirjoitusoikeus? rivit-atom tyyppi voi-muokata? ohjauskahva]
  (assert (#{:alusta :paallystekerros} tyyppi) "Tyypin on oltava päällystekerros tai alusta")
  (let [lisatoiminnot-auki? (atom false)]
    (fn [rivi {:keys [index] :as osa} e! app kirjoitusoikeus? rivit-atom tyyppi voi-muokata? ohjauskahva]
      (let [nappi-disabled? (or (not voi-muokata?)
                                (not kirjoitusoikeus?))
            sort-atom (case tyyppi
                        :alusta pot2-tiedot/valittu-alustan-sort
                        :paallystekerros pot2-tiedot/valittu-paallystekerros-sort)]
        [:span.tasaa-oikealle.pot2-rivin-toiminnot
         ;; vain sille riville tarjotaan undo, missä on toimintoa painettu
         (if (and (= tyyppi (:tyyppi @undo-tiedot))
                  (= index (:index @undo-tiedot)))
           [:div
            [napit/yleinen-toissijainen "Peru toiminto"
             #(do
                (reset! rivit-atom @edellinen-tila)
                (poista-undo-tiedot)
                (when ohjauskahva (grid/validoi-grid ohjauskahva)))]]
           [:<>
            (for*
              [rivin-toiminto (rivin-toiminnot e! ohjauskahva nappi-disabled? rivi rivit-atom sort-atom tyyppi index)]
              [napit/nappi-hover-vihjeella rivin-toiminto])
            [rivin-lisatoiminnot-dropdown (rivin-toiminnot e! ohjauskahva nappi-disabled? rivi rivit-atom sort-atom tyyppi index)
             lisatoiminnot-auki?]
            (when (= "epaonnistunut" (:velho-rivi-lahetyksen-tila rivi))
              (lahetys-virheet-nappi rivi :lyhyt))])]))))


