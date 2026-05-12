(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.renderointi
	(:require [harja.ui.modal :as modal]
					[harja.ui.primitiivit.viesti :as viesti]
					[harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys :as kehys]))

(defn renderoi-viesti-parametreista [{:keys [variantti sisalto]}]
	(let [{viestin-luokka :sisalto
		   variantin-luokka :variantti}
			(viesti/flash-viestin-luokat variantti)]
		[:div {:class "ui-komponenttien-tarkastelu-viesti"}
		 [:div {:class (str viestin-luokka " " variantin-luokka)}
		  sisalto]]))

(defn renderoi-modaalin-parametreista [{:keys [modaalin-tila]}
								 {:keys [avaus-teksti footer-teksti otsikko sisalto-rivit]}]
	(let [avaa-modali! #(swap! modaalin-tila assoc :nakyvissa? true)
			sulje-modali! #(swap! modaalin-tila assoc :nakyvissa? false)
			footer-nappi [:button {:type "button"
								 :class "nappi-toissijainen"
								 :data-cy "ui-komponenttien-tarkastelu-sulje-modaali"
								 :on-click sulje-modali!}
					  footer-teksti]]
		[:<>
		 [:div {:class "ui-komponenttien-tarkastelu-toiminnot"}
		  [:button {:type "button"
						 :class "nappi-toissijainen"
						 :data-cy "ui-komponenttien-tarkastelu-avaa-modaali"
						 :on-click avaa-modali!}
		   avaus-teksti]]
		 [modal/modal {:otsikko otsikko
						 :nakyvissa? (:nakyvissa? @modaalin-tila)
						 :sulje-fn sulje-modali!
						 :footer footer-nappi}
		  [:div {:data-cy "ui-komponenttien-tarkastelu-modaali-sisalto"}
		   (into [:<>] (map (fn [rivi] [:p rivi]) sisalto-rivit))]]]))

(defn- renderoi-komponentin-sisalto [konteksti {:keys [tyyppi parametrit renderoi-esikatselu] :as komponentti}]
	(cond
		renderoi-esikatselu (renderoi-esikatselu konteksti komponentti)
		(= tyyppi :viesti) (renderoi-viesti-parametreista parametrit)
		(= tyyppi :modaali) (renderoi-modaalin-parametreista konteksti parametrit)
		:else [:div {:class "ui-komponenttien-tarkastelu-virhe"}
			   "Komponentin esikatselua ei ole määritelty."]))

(defn renderoi-komponentti-kortti [konteksti {:keys [id nimi kuvaus data-cy] :as komponentti}]
	[kehys/tarkastelu-kortti {:id id
							 :otsikko nimi
							 :kuvaus kuvaus
							 :data-cy data-cy
							 :sisalto (renderoi-komponentin-sisalto konteksti komponentti)}])

(defn renderoi-komponentit [konteksti komponentit]
	(into [:<>] (map #(renderoi-komponentti-kortti konteksti %) komponentit)))
