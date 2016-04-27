(ns harja.views.urakka.yhatuonti
  (:require [reagent.core :refer [atom] :as reagent]
            [harja.ui.modal :as modal]
            [harja.ui.yleiset :refer [ajax-loader vihje]]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.ui.lomake :as lomake]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.ui.grid :refer [grid]]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defn- hakulomake []
  [lomake/lomake {:otsikko "Urakan tiedot"
           :muokkaa! (fn [uusi-data]
                       (reset! yha/hakulomake-data uusi-data))}
   [{:otsikko "Nimi"
     :nimi :nimi
     :pituus-max 512
     :tyyppi :string}
    {:otsikko "Tunniste"
     :nimi :tunniste
     :pituus-max 512
     :tyyppi :string}
    {:otsikko "Vuosi"
     :nimi :vuosi
     :pituus-max 512
     :tyyppi :positiivinen-numero}]
   @yha/hakulomake-data])

(defn- hakutulokset [urakka]
  (let [sidonta-kaynnissa? @yha/sidonta-kaynnissa?]
    [grid
     {:otsikko "Löytyneet urakat"
      :tyhja (if (nil? @yha/hakutulokset-data) [ajax-loader "Haetaan..."] "Urakoita ei löytynyt")
      :tunniste :tunnus}
     [{:otsikko "Tunnus"
       :nimi :tunnus
       :tyyppi :string
       :muokattava? (constantly false)}
      {:otsikko "Nimi"
       :nimi :nimi
       :tyyppi :string
       :muokattava? (constantly false)}
      {:otsikko "ELYt"
       :nimi :elyt
       :tyyppi :string
       :muokattava? (constantly false)}
      {:otsikko "Vuodet"
       :nimi :vuodet
       :tyyppi :string
       :muokattava? (constantly false)}
      {:otsikko "Sidonta"
       :nimi :valitse
       :tyyppi :komponentti
       :komponentti (fn [rivi]
                      [:button.nappi-ensisijainen.nappi-grid
                       {:on-click #(yha/sido-yha-urakka-harja-urakkaan (:id urakka) rivi)
                        :disabled sidonta-kaynnissa?}
                       "Valitse"])}]
     @yha/hakutulokset-data]))

(defn- sidonta-kaynnissa []
  [ajax-loader "Sidonta käynnissä..."])

(defn- tuontidialogi [urakka optiot]
  [:div
   (if (:sitomaton-urakka? optiot)
     [vihje "Urakka täytyy sitoa YHA:n vastaavaan urakkaan tietojen siirtämiseksi Harjaan. Etsi YHA-urakka täyttämällä vähintään yksi hakuehto ja tee sidonta."]
     [lomake/yleinen-varoitus (str "Urakka on jo sidottu YHA-urakkaan " (get-in urakka [:yha-tiedot :nimi]) ". Jos vaihdat sidonnan toiseen urakkaan, kaikki Harja-urakkaan tuodut kohteet poistetaan.")])
   [hakulomake]
   [hakutulokset urakka]
   (when @yha/sidonta-kaynnissa?
     [sidonta-kaynnissa])])

(defn nayta-tuontidialogi [urakka]
  (modal/nayta!
    {:otsikko "Urakan sitominen YHA-urakkaan"
     :luokka "yha-tuonti"
     :footer [:button.nappi-toissijainen {:on-click (fn [e]
                                                      (.preventDefault e)
                                                      (modal/piilota!))}
              "Sulje"]}
    (tuontidialogi urakka {:sitomaton-urakka? (nil? (:yha-tiedot urakka))})))