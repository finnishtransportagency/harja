(ns harja.ui.primitiivit.viesti-test
	(:require [clojure.test :refer [deftest is testing]]
						[harja.ui.primitiivit.viesti :as viesti]))

(deftest flash-viestin-luokat-palauttaa-harjan-omat-luokat
	(testing "onnistumisvariantti palauttaa oman luokkamappauksen"
		(is (= {:overlay "harja-viesti-overlay"
						:tausta "harja-viesti-tausta"
						:runko "harja-viesti-runko"
						:sisalto "harja-viesti"
						:variantti "harja-viesti-onnistuminen"}
					(viesti/flash-viestin-luokat :success))))

	(testing "tuntematon luokka putoaa info-varianttiin"
		(is (= "harja-viesti-info"
					(:variantti (viesti/flash-viestin-luokat :tuntematon))))))

(deftest kehityssivun-viesti-esimerkit-ovat-vakaat
	(let [ryhma viesti/kehityssivun-viesti-esimerkit
				esimerkit (:esimerkit ryhma)]
		(is (= :viesti (:id ryhma)))
		(is (= "primitive-viesti-ryhma" (:data-cy ryhma)))
		(is (= ["primitive-viesti-onnistuminen"
						"primitive-viesti-info"
						"primitive-viesti-varoitus"]
					(mapv :data-cy esimerkit)))
		(is (= [:success :info :warning]
					(mapv :luokka esimerkit)))))
