(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.simulaattori
  "Rekisteröidyistä shared-komponenteista rakennettu rajattu sivusimulaattori."
  (:require [reagent.core :as r]
            [harja.ui.komponentit :as ui]
            [harja.ui.napit :as napit]
            [harja.ui.komponentit.simulaattori :as sopimus]))

(def ^:private esimerkkipuu
  [{:komponentti :lomake
    :lapset [{:komponentti :valintaryhma
              :asetukset {:otsikko "Urakkatyyppi"
                          :vaihtoehdot [{:arvo :hoito :nimi "Hoito"}
                                        {:arvo :paallyste :nimi "Päällyste"}
                                        {:arvo :vesivayla :nimi "Vesiväylä"}]}}
             {:komponentti :nappi
              :asetukset {:teksti "Tallenna"}}]}])

(defn- solmun-sisalto [solmu tila]
  (case (:komponentti solmu)
    :lomake
    [ui/pino {:data-cy "ui-simulaattori-lomake"}
     (map-indexed (fn [indeksi lapsi]
                    ^{:key indeksi}
                    [solmun-sisalto lapsi tila])
                  (:lapset solmu))]

    :valintaryhma
    [ui/valintaryhma
     (merge {:id "ui-simulaattori-urakkatyyppi"
             :arvo @(:valinta tila)
             :vaihda-fn (fn [arvo] (reset! (:valinta tila) arvo))
             :ohje "Valinta tulee deklaratiivisen skenaarion vaihtoehdoista."}
            (:asetukset solmu))]

    :nappi
    [napit/nappi
     (get-in solmu [:asetukset :teksti])
     (fn [] (reset! (:tila tila) :onnistui))
     {:data-cy "ui-simulaattori-tallenna"}]

    [:p {:role "alert"} (str "Komponenttia ei voi renderöidä: " (:komponentti solmu))]))

(defn- puu [tila]
  (if-let [virheet (seq (sopimus/virheet esimerkkipuu))]
    [:div {:data-cy "ui-simulaattori-virheet"}
     [:h2 "Simulaattorin rakenne ei ole kelvollinen"]
     (for [virhe virheet]
       ^{:key virhe} [:p virhe])]
    [:div {:data-cy "ui-simulaattori-esikatselu"}
     (for [solmu esimerkkipuu]
       ^{:key (:komponentti solmu)} [solmun-sisalto solmu tila])]
    ))

(defn sivu []
  (r/with-let [valinta (r/atom :hoito)
               tila (r/atom :normaali)]
    (let [tila-atomi {:valinta valinta :tila tila}]
      [:main {:data-cy "ui-komponenttien-sivusimulaattori"}
       [:header
        [:p "Deklaratiivinen näkymä"]
        [:h2 "Sivusimulaattori"]
        [:p "Skenaario rakennetaan rekisteröidyistä komponenteista ilman suoritettavaa agenttikoodia."]]
       [ui/pino {:data-cy "ui-simulaattori"}
        [puu tila-atomi]
        [:p {:data-cy "ui-simulaattori-tila"}
         (str "Tila: " (name @tila))]]])))
