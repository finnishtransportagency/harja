(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys)

(defn osion-otsikko [{:keys [otsikko kuvaus otsikkotagi]}]
	(let [otsikkotagi (or otsikkotagi :h2)]
		[:div {:class "ui-komponenttien-tarkastelu-osion-otsikko"}
		 [otsikkotagi otsikko]
		 [:p kuvaus]]))

(defn tarkastelu-osio [{:keys [data-cy sisalto] :as osio}]
	[:section {:class "ui-komponenttien-tarkastelu-osio"
				 :data-cy data-cy}
	 [osion-otsikko osio]
	 sisalto])

(defn tarkastelu-kortti [{:keys [id otsikko kuvaus data-cy sisalto]}]
	(let [kortti
			[:article {:class "ui-komponenttien-tarkastelu-kortti"
						 :data-cy data-cy}
			 [:div {:class "ui-komponenttien-tarkastelu-kortin-tekstit"}
				[:h3 otsikko]
				[:p kuvaus]]
			 sisalto]]
		(if id
			(with-meta kortti {:key (name id)})
			kortti)))
