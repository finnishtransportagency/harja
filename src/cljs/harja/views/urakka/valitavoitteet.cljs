(ns harja.views.urakka.valitavoitteet
  "Ylläpidon urakoiden välitavoitteiden näkymä"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.valitavoitteet :as vt]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as y]
            [harja.pvm :as pvm]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.fmt :as fmt]
            [cljs-time.core :as t]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.oikeudet :as oikeudet]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [harja.ui.yleiset :as yleiset])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(def tallennus-kaynnissa? (atom false))

(defn valmiustilan-kuvaus [{:keys [valmis takaraja]}]
  (if-not takaraja
    "Uusi"
    (let [valmis-pvm (:pvm valmis)]
      (if valmis-pvm
        (str "Valmistunut " (pvm/pvm valmis-pvm))
        (if (t/after? (pvm/nyt) takaraja)
          "Myöhässä"
          "Ei valmis")))))

(defn valitavoite-valmis-lomake [_ ur vt]
  (let [valmis-pvm (atom nil)
        kommentti (atom "")
        tallennus-kaynnissa (atom false)]
    (fn [{aseta-tavoitteet :aseta-tavoitteet} ur vt]
      [:div {:style {:position "relative"}}
       (when @tallennus-kaynnissa
         (y/lasipaneeli (y/keskita (y/ajax-loader))))
       [:form
        [:div.form-group
         [:label {:for "valmispvm"} "Valmistumispäivä"]
         [tee-kentta {:tyyppi :pvm}
          valmis-pvm]]

        [:div.form-group
         [:label {:for "kommentti"} "Kommentti"]
         [:textarea#kommentti.form-control {:on-change #(reset! kommentti (-> % .-target .-value))
                                            :rows 3
                                            :value @kommentti}]]

        [:div.toiminnot
         [:button.nappi-ensisijainen
          {:disabled (nil? @valmis-pvm)
           :on-click #(do (.preventDefault %)
                          (reset! tallennus-kaynnissa true)
                          (go (let [res (<! (vt/merkitse-valmiiksi! (:id ur) (:id vt)
                                                                    @valmis-pvm @kommentti))]
                                (if (k/virhe? res)
                                  (viesti/nayta! "Valmiiksi merkitseminen epäonnistui" :warning viesti/viestin-nayttoaika-lyhyt)
                                  (do
                                    (aseta-tavoitteet res)
                                    (reset! tallennus-kaynnissa false))))))}
          "Merkitse valmiiksi"]]]])))

(defn valitavoite-lomake [opts ur vt]
  (let [{:keys [pvm merkitsija merkitty kommentti]} (:valmis vt)]
    [:div.valitavoite
     [:div.valmis
      (when pvm
        [y/rivi
         {:koko y/tietopaneelin-elementtikoko}


         [y/otsikolla "Valmistunut" (fmt/pvm pvm)]

         (when merkitty
           [y/otsikolla "Merkitty valmiiksi"
            [:span (fmt/pvm-opt merkitty) " " (fmt/kayttaja-opt merkitsija)]])


         (when kommentti
           [y/otsikolla "Urakoitsijan kommentti" kommentti])])]

     (when (and (nil? pvm)
                (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet (:id ur)))
       ;; Ei ole valmis, sallitaan urakoitsijan käyttäjän merkitä se valmiiksi
       [valitavoite-valmis-lomake opts ur vt])]))

(defn- urakan-valitavoitteet [urakka
                              kaikki-valitavoitteet-atom
                              urakan-valitavoitteet-atom]
  (log "[BUG] " (pr-str @urakan-valitavoitteet-atom))
  [grid/grid
   {:otsikko "Urakan välitavoitteet"
    :tyhja (if (nil? @urakan-valitavoitteet-atom)
             [y/ajax-loader "Välitavoitteita haetaan..."]
             "Ei välitavoitteita")
    :tallenna (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet (:id urakka))
                #(go (reset! tallennus-kaynnissa? true)
                     (go
                       (let [vastaus (<! (vt/tallenna-valitavoitteet! (:id urakka) %))]
                         (if (k/virhe? vastaus)
                           (viesti/nayta! "Tallentaminen epäonnistui"
                                          :warning viesti/viestin-nayttoaika-lyhyt)
                           (reset! kaikki-valitavoitteet-atom vastaus)))
                       (reset! tallennus-kaynnissa? false))))

    :vetolaatikot (into {}
                        (map
                          (juxt :id
                                (partial valitavoite-lomake
                                         {:aseta-tavoitteet
                                          #(reset! kaikki-valitavoitteet-atom %)} urakka)))
                        @urakan-valitavoitteet-atom)}

   [{:tyyppi :vetolaatikon-tila :leveys 5}
    {:otsikko "Nimi" :leveys 55 :nimi :nimi :tyyppi :string :pituus-max 128}
    {:otsikko "Taka\u00ADraja" :leveys 20 :nimi :takaraja :fmt #(if %
                                                           (pvm/pvm-opt %)
                                                           "Ei takarajaa")
     :tyyppi :pvm}
    {:otsikko "Tila" :leveys 25 :tyyppi :string :muokattava? (constantly false)
     :nimi :valmiustila :hae identity :fmt valmiustilan-kuvaus}]
   @urakan-valitavoitteet-atom])

(defn- valtakunnalliset-valitavoitteet [urakka
                                        kaikki-valitavoitteet-atom
                                        valtakunnalliset-valitavoitteet-atom]
  [:div
   [grid/grid
   {:otsikko "Valtakunnalliset välitavoitteet"
    :tyhja (if (nil? @valtakunnalliset-valitavoitteet-atom)
             [y/ajax-loader "Välitavoitteita haetaan..."]
             "Ei välitavoitteita")
    :tallenna (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet (:id urakka))
                #(go (reset! tallennus-kaynnissa? true)
                     (go
                       (let [vastaus (<! (vt/tallenna-valitavoitteet! (:id urakka) %))]
                         (if (k/virhe? vastaus)
                           (viesti/nayta! "Tallentaminen epäonnistui"
                                          :warning viesti/viestin-nayttoaika-lyhyt)
                           (reset! kaikki-valitavoitteet-atom vastaus)))
                       (reset! tallennus-kaynnissa? false))))
    :vetolaatikot (into {}
                        (map
                          (juxt :id
                                (partial valitavoite-lomake
                                         {:aseta-tavoitteet
                                          #(reset! kaikki-valitavoitteet-atom %)} urakka)))
                        @valtakunnalliset-valitavoitteet-atom)
    :voi-lisata? false
    :voi-poistaa? (constantly false)}

   [{:tyyppi :vetolaatikon-tila :leveys 5}
    {:otsikko "Valta\u00ADkunnal\u00ADlinen väli\u00ADtavoite" :leveys 55
     :nimi :valtakunnallinen-nimi :tyyppi :string :pituus-max 128
     :muokattava? (constantly false) :hae #(str (:valtakunnallinen-nimi %))}
    {:otsikko "Väli\u00ADtavoite ura\u00ADkassa" :leveys 55 :nimi :nimi :tyyppi :string :pituus-max 128}
    {:otsikko "Valta\u00ADkunnal\u00ADlinen taka\u00ADraja" :leveys 20
     :nimi :valtakunnallinen-takaraja :hae #(cond
                                             (:valtakunnallinen-takaraja %)
                                             (pvm/pvm-opt (:valtakunnallinen-takaraja %))

                                             (and (:valtakunnallinen-takarajan-toistopaiva %)
                                                  (:valtakunnallinen-takarajan-toistokuukausi %))
                                             (str "Vuosittain "
                                                  (:valtakunnallinen-takarajan-toistopaiva %)
                                                  "."
                                                  (:valtakunnallinen-takarajan-toistokuukausi %))

                                             :default
                                             "Ei takarajaa")
     :tyyppi :pvm
     :muokattava? (constantly false)}
    {:otsikko "Taka\u00ADraja ura\u00ADkassa" :leveys 20 :nimi :takaraja :fmt pvm/pvm-opt :tyyppi :pvm}
    {:otsikko "Tila" :leveys 25 :tyyppi :string :muokattava? (constantly false)
     :nimi :valmiustila :hae identity :fmt valmiustilan-kuvaus}]
   @valtakunnalliset-valitavoitteet-atom]
   [yleiset/vihje "Valtakunnalliset välitavoitteet ovat järjestelmävastaavan hallinnoimia. Voit kuitenkin tehdä tavoitteisiin urakkakohtaisia muokkauksia."]])

(defn valitavoitteet
  "Urakan välitavoitteet näkymä. Ottaa parametrinä urakan ja hakee välitavoitteet sille."
  [ur]
  (let [tallennus-kaynnissa (atom false)]
    (komp/luo
      (komp/lippu vt/nakymassa?)
      (fn [ur]
        [:div.valitavoitteet {:style {:position "relative"}}
         (when @tallennus-kaynnissa (y/lasipaneeli (y/keskita (y/ajax-loader))))
         [urakan-valitavoitteet
          ur
          vt/valitavoitteet
          vt/urakan-valitavoitteet]
         [valtakunnalliset-valitavoitteet
          ur
          vt/valitavoitteet
          vt/valtakunnalliset-valitavoitteet]

         ;; PENDING Kommentoidaan toistaiseksi tämä ylläpidon demo pois hämmentämästä
         ;; Ylläpidon välitavoitteita pohditaan myöhemmin
         #_(when (and (= (:sopimustyyppi @nav/valittu-urakka) :kokonaisurakka)
                      (or (= (:tyyppi @nav/valittu-urakka) :paallystys)
                          (= (:tyyppi @nav/valittu-urakka) :paikkaus)))
             [grid/grid
              {:otsikko "Kohteiden välitavoitteet"
               :tunniste :yha-id
               :tallenna nil
               :vetolaatikot (into {}
                                   (map
                                     (juxt :id
                                           (partial valitavoite-lomake
                                                    {:aseta-tavoitteet #(reset! vt/valitavoitteet %)} ur)))
                                   @vt/valitavoitteet)
               }

              [{:tyyppi :vetolaatikon-tila :leveys "5%"}
               {:otsikko "YHA-ID" :leveys "15%" :nimi :yha-id :tyyppi :string :pituus-max 128 :muokattava? (constantly false)}
               {:otsikko "Kohde" :leveys "60%" :nimi :kohde :tyyppi :string :muokattava? (constantly false)}
               {:otsikko "Tila" :leveys "20%" :tyyppi :string :muokattava? (constantly false)
                :nimi :tila}]
              [{:yha-id 1 :kohde "Mt 22 Ruohonjuuren pätkä" :tila "Kaikki valmiina"}
               {:yha-id 2 :kohde "Mt 22 Terilän silta" :tila "Kaikki valmiina"}
               {:yha-id 3 :kohde "Mt 22 Matulan  pätkä" :tila "Kohde kesken"}
               {:yha-id 4 :kohde "Mt 22 koskenlaskijan kuru" :tila "Kohde kesken"}
               {:yha-id 5 :kohde "Mt 22 rampit" :tila "Kaikki valmiina"}]])]))))
