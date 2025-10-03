(ns harja.kyselyt.materiaalit
  (:require [jeesql.core :refer [defqueries]]))

(defqueries "harja/kyselyt/materiaalit.sql"
  {:positional? true})

(declare hae-materiaalikoodit listaa-materiaalikoodit hae-materiaaliluokat
  hae-urakan-suunniteltu-materiaalin-kaytto-analytiikalle hae-talvisuolan-materiaaliluokka
  hae-urakan-toteutuneet-materiaalit-raportille hae-urakan-suunnitellut-materiaalit-raportille
  hae-hallintayksikon-toteutuneet-materiaalit-raportille hae-koko-maan-toteutuneet-materiaalit-raportille)
