(ns harja.kyselyt.sanktio-konfiguraatio
	(:require [harja.kyselyt.konversio :as konv]
						[jeesql.core :refer [defqueries]]))

(defn- muunna-urakkatyyppi
	[rivi avainpolku]
	(if (get-in rivi avainpolku)
		(update-in rivi avainpolku keyword)
		rivi))

(defn- muunna-soveltuvuuskontekstit
	[rivi]
	(cond-> rivi
		(:soveltuvuuskontekstit rivi)
		(->
			(konv/array->vec :soveltuvuuskontekstit)
			(update :soveltuvuuskontekstit #(mapv keyword %)))))

(defn muunna-sanktio-profiili
	[{:as rivi}]
	(let [rivi (konv/alaviiva->rakenne rivi)]
		(muunna-urakkatyyppi rivi [:urakkatyyppi])))

(defn muunna-sanktio-profiili-admin-listarivi
	[{:as rivi}]
	(-> rivi
		konv/alaviiva->rakenne
		(muunna-urakkatyyppi [:urakkatyyppi])
		muunna-soveltuvuuskontekstit))

(defn- normalisoi-profiilirivin-metatiedot
	[rivi]
	(let [voi-puolittaa-omailmoituksella (or (get-in rivi [:profiilirivi :voi-puolittaa-omailmoituksella])
														 (get-in rivi [:profiilirivi :voi :puolittaa :omailmoituksella]))
			lukitut-summat (or (get-in rivi [:profiilirivi :lukitut-summat])
						   (get-in rivi [:profiilirivi :lukitut :summat]))]
		(cond-> rivi
			(some? voi-puolittaa-omailmoituksella)
			(assoc-in [:profiilirivi :voi-puolittaa-omailmoituksella] voi-puolittaa-omailmoituksella)

			(some? lukitut-summat)
			(assoc-in [:profiilirivi :lukitut-summat] lukitut-summat)

			(get-in rivi [:profiilirivi :voi])
			(update :profiilirivi dissoc :voi)

			(get-in rivi [:profiilirivi :lukitut])
			(update :profiilirivi dissoc :lukitut))))

(defn muunna-sanktio-konfiguraatiorivi
	[{:as rivi}]
	(let [rivi (-> rivi
				 konv/alaviiva->rakenne
				 normalisoi-profiilirivin-metatiedot)]
		(cond-> rivi
			(get-in rivi [:profiilirivi :lukitut-summat])
			(update-in [:profiilirivi :lukitut-summat]
				#(cond
					(nil? %) []
					(vector? %) %
					(instance? java.sql.Array %) (vec (.getArray ^java.sql.Array %))
					:else [%]))

			(get-in rivi [:profiili :urakkatyyppi])
			(muunna-urakkatyyppi [:profiili :urakkatyyppi])

			(:soveltuvuuskonteksti rivi)
			(update :soveltuvuuskonteksti keyword)

			(get-in rivi [:laji :koodi])
			(update-in [:laji :koodi] keyword))))

(defqueries "harja/kyselyt/sanktio_konfiguraatio.sql")

(declare hae-urakan-sanktio-profiilit)
(declare hae-sanktio-profiilit-admin)
(declare hae-sanktio-profiili-admin)
(declare hae-sanktio-profiilin-rivit)
(declare hae-sanktio-profiilin-rivit-admin)
