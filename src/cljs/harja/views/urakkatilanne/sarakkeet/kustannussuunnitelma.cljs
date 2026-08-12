(ns harja.views.urakkatilanne.sarakkeet.kustannussuunnitelma
  (:require [harja.ui.yleiset :as yleiset]
            [harja.tiedot.urakka.siirtymat :as siirtymat]))


(defn kustannussuunitelman-tila-sarake
  [rivi]
  (let [indeksi-saatavilla? (boolean (:indeksikerroin rivi))
        {:keys [aloittamattomia vahvistamattomia vahvistettuja suunnitelman_tila]} (:ks_tila rivi)]
    [yleiset/wrap-if true
     [yleiset/tooltip {} :% "Siirry kustannussuunnitelmaan"]
     [:a.klikattava.alleviivaa {:href (str "/#urakat/suunnittelu/kustannussuunnitelma?&hy=" (:evk_id rivi) "&u=" (:id rivi))
                                :on-click #(siirtymat/siirry-annettuun-valilehteen
                                             (:evk_id rivi)
                                             (:id rivi)
                                             {:taso1 :urakat
                                              :taso2 :suunnittelu
                                              :taso3 :kustannussuunnitelma})}
      (cond
        (= "aloittamatta" suunnitelman_tila)
        (yleiset/tila-indikaattori "hylatty" {:fmt-fn (constantly "Aloittamatta")})

        (= "aloitettu" suunnitelman_tila)
        (yleiset/tila-indikaattori (if indeksi-saatavilla?
                                     "hylatty"
                                     "kesken")
          {:fmt-fn #(str
                      (when indeksi-saatavilla? "Indeksi saatavilla. ")
                      "Aloittamatta: " aloittamattomia
                      ", kesken: " vahvistamattomia
                      ", vahvistettuja: " vahvistettuja)})

        (= "vahvistettu" suunnitelman_tila)
        (yleiset/tila-indikaattori "valmis" {:fmt-fn (constantly "Valmis")}))]]))
