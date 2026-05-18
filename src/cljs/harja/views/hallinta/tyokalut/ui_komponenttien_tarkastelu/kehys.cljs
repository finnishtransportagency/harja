(ns harja.views.hallinta.tyokalut.ui-komponenttien-tarkastelu.kehys)

(def ^:private bootstrap-tilat
	{:portattu {:teksti "Portattu pois Bootstrapista"
				 :luokka "ui-komponenttien-tarkastelu-porttaustila-portattu"}
	 :osittain-portattu {:teksti "Osittain portattu pois Bootstrapista"
						 :luokka "ui-komponenttien-tarkastelu-porttaustila-osittain-portattu"}
	 :bootstrap-riippuvainen {:teksti "Yhä Bootstrap-riippuvainen"
							 :luokka "ui-komponenttien-tarkastelu-porttaustila-bootstrap-riippuvainen"}
	 :määrittelemättä {:teksti "Bootstrap-tila määrittelemättä"
					 :luokka "ui-komponenttien-tarkastelu-porttaustila-maarittelematta"}})

(defn komponentin-porttaustieto [{:keys [bootstrap-tila bootstrap-kuvaus]}]
	(let [{:keys [teksti luokka]}
			(or (get bootstrap-tilat bootstrap-tila)
				(get bootstrap-tilat :määrittelemättä))]
		[:div {:class "ui-komponenttien-tarkastelu-porttaustieto"}
		 [:span {:class (str "ui-komponenttien-tarkastelu-porttaustila " luokka)}
		  teksti]
		 (when bootstrap-kuvaus
			[:p {:class "ui-komponenttien-tarkastelu-porttaustiedon-kuvaus"}
			 bootstrap-kuvaus])]))

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

(defn tarkastelu-kortti [{:keys [id otsikko kuvaus data-cy lisatiedot sisalto]}]
	(let [kortti
			[:article {:class "ui-komponenttien-tarkastelu-kortti"
						 :data-cy data-cy}
			 [:div {:class "ui-komponenttien-tarkastelu-kortin-tekstit"}
				[:h3 otsikko]
				[:p kuvaus]
				lisatiedot]
			 sisalto]]
		(if id
			(with-meta kortti {:key (name id)})
			kortti)))
