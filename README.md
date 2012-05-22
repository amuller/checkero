checkero
========

Similar clojure code searcher.

Checkero finds common clojure source code inside a set of directories.
The primary usage is to study how Clojure learners write functions.
As a side effect you can find if students have honestly completed their
homework. It could also be used to find commonly used patterns in code
that require refactoring. The algorithm uses a state-of-the-art
Tree distance function that quickly finds common tree patterns. 
It analyzes the syntactical structure of the program and finds similar
expressions. 


Usage
-----

   java -jar checkero-1.0.0-SNAPSHOT-standalone.jar 8 100 24 30 source-folder

   Parameters are inserted in order:

   mnode: Minimum number of syntax nodes per expression to use. (short expressions are too common) 
   h: Get the top "h" hotspot expressions in the directories
   k: Get "k" closest matches per query
   range: Get matches that are at most n different complete subtrees. 
   source-folder: A folder that contains n folders for n students. 
                  Each sub-folder inside "source-folder" will be treated 
                  as one student homework.


Output
------

The script finds common subexpressions and prints some global statistics.

Hotspots
--------

Prints out the most commonly used subexpressions.

For example:

  @@@ Commonly found expressions in the homework folder:
  >>>        Student-Name
              [/path/Core.clj]
  [Original]   (defn distance [user-seq matrix] (create-Matrix user-seq matrix))
  [Normalized] (defn "s0" ["s1" "s2"] ("s3" "s1" "s2"))
  [Multiplicity]    237

Here you can see:
1) The student responsible of the common expression.
2) Path of the expression
3) Original expression
4) Normalized expression (this is the expression actually used in the match)
5) Multiplicity: The number of times this hotspot appears in the entire set of files.


Friendship Graph
----------------

This section of the program output intends to predict how
close students are to each other. The output looks like:

  ###  <Student0>
  <| 0 [Student1 4] [Student2 2] [Student3 2] [Student4 2] [Student5 2]                                                                 
  <| 3 [Student4 1] |>

This reads as follows: 

For expressions that have distance 0, Student 0 has:

* 4 matches with student1
* 2 matches with student2
* 2 matches with student3
* 2 matches with student4
* 2 matchew with student5

For expressions that have distance 3, Student 0 has:

* 1 match with student4


Output per Directory
--------------------


Besides the output in stdout, checkero creates a "checkero.txt" file on each student folder that
contains details of the search. 

  -------------------------------Student code: Student1

  [Query]
  Original:   (ns TareaBindingSites.core (:gen-class) (:require [clojure.java.io :as io] [clojure.string :as string]))
  Normalized: (ns "s0" ("k1") ("k2" ["s3" "k4" "s5"] ["s6" "k4" "s7"]))
  '>>> student3 [/path/binding.clj]
  Distance:   0
  Original:   (ns genome-project.binding (:gen-class) (:require [clojure.java.io :as io] [clojure.string :as string]))
  Normalized: (ns "s0" ("k1") ("k2" ["s3" "k4" "s5"] ["s6" "k4" "s7"]))
  '>>> student10 [/path/core.clj]
  Distance:   6
  Original:   (ns Cromossoma.core (:gen-class) (:require [clojure.java.io :as io]) (:require [clojure.string :as string]))
  Normalized: (ns "s0" ("k1") ("k2" ["s3" "k4" "s5"]) ("k2" ["s6" "k4" "s7"]))
  '>>> student20 [/path/core.clj]
  Distance:   8
  Original:   (ns homework.core (:gen-class) (:require [clojure.java.io :as io]))
  Normalized: (ns "s0" ("k1") ("k2" ["s3" "k4" "s5"]))


The student name is stated at the beginning of the file.
Each [query] entry shows the code that was found in Student1's folder.
Each ">>>" entry describes a close match against another student. 
The case shown is trivial, namespace definitions tend to be written in a very
similar form. 
