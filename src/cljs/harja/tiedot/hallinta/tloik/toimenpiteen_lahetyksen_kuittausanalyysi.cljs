(ns harja.tiedot.hallinta.tloik.toimenpiteen-lahetyksen-kuittausanalyysi
	(:require [cljs.core.async :refer [<!]]
					[harja.asiakas.kommunikaatio :as k])
	(:require-macros [cljs.core.async.macros :refer [go]]))

(def ei-kaytossa :ei-kaytossa)
(defonce epaillyt-duplikaattikuittaukset (atom ei-kaytossa))
(defonce kuittausanalyysin-hakuversio (atom 0))

(def duplikaattikuittaukset-ladataan :ladataan)
(def duplikaattikuittaukset-epaonnistui :epaonnistui)
(def kuittausanalyysi-endpoint :hae-tloik-toimenpiteen-lahetyksen-kuittausanalyysi)

(defn nayta-kuittausanalyysi?
	[jarjestelma integraatio]
	(and (= "tloik" (:jarjestelma jarjestelma))
			 (= "toimenpiteen-lahetys" integraatio)))

(defn- hae-toimenpiteen-lahetyksen-kuittausanalyysi
	[aikavali]
	(k/post! kuittausanalyysi-endpoint
					 (when aikavali
						 {:alkaen (first aikavali)
							:paattyen (second aikavali)})))

(defn- seuraava-hakuversio! []
	(swap! kuittausanalyysin-hakuversio inc))

(defn- aseta-hakutulos!
	[hakuversio vastaus]
	(when (= hakuversio @kuittausanalyysin-hakuversio)
		(reset! epaillyt-duplikaattikuittaukset
					(if (k/virhe? vastaus)
						duplikaattikuittaukset-epaonnistui
						vastaus))))

(defn poista-kaytosta!
	[]
	(seuraava-hakuversio!)
	(reset! epaillyt-duplikaattikuittaukset ei-kaytossa))

(defn paivita-kuittausanalyysi!
	[jarjestelma integraatio aikavali]
	(if (nayta-kuittausanalyysi? jarjestelma integraatio)
		(let [hakuversio (seuraava-hakuversio!)]
			(reset! epaillyt-duplikaattikuittaukset duplikaattikuittaukset-ladataan)
			(go (aseta-hakutulos! hakuversio
												(<! (hae-toimenpiteen-lahetyksen-kuittausanalyysi aikavali)))))
		(poista-kaytosta!)))
