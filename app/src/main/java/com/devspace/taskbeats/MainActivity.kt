package com.devspace.taskbeats

import android.icu.text.CaseMap.Title
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    // variaveis globais, está visível para todas as funções da classe
    private var categories = listOf<CategoryUiData>()
    private var categoriesEntity = listOf<CategoryEntity>()

    private var tasks = listOf<TaskUiData>()

    private val categoryAdapter = CategoryListAdapter()

    private val taskAdapter by lazy {
        TaskListAdapter()
    }

    //by lazy só inicia o codigo quando usa a base de dados
    val db by lazy {
        Room.databaseBuilder(
            applicationContext,
            TaskBeatDataBase::class.java, "database-task-beat"
        ).build()
    }

    private val categoryDao: CategoryDao by lazy {
        db.getCategoryDao()
    }

    private val taskDao: TaskDao by lazy {
        db.getTaskDao()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val rvCategory = findViewById<RecyclerView>(R.id.rv_categories)
        val rvTask = findViewById<RecyclerView>(R.id.rv_tasks)
        val fabCreateTask = findViewById<FloatingActionButton>(R.id.fab_create_task)

        fabCreateTask.setOnClickListener {
            showCreateUpdateTaskBottomSheet()

        }

        taskAdapter.setOnClickListener { task ->
            showCreateUpdateTaskBottomSheet(task)
        }

        categoryAdapter.setOnLongClickListener { categoryToBeDeleted ->
            if (categoryToBeDeleted.name != "+" && categoryToBeDeleted.name != "ALL") {
                val title = this.getString(R.string.category_delete_title)
                val description = this.getString(R.string.category_delete_description)
                val btnText: String = this.getString(R.string.category_delete_button)

                showInfoDialog(
                    title,
                    description,
                    btnText
                ) {
                    val catergoryEntityToBeDeleted = CategoryEntity(
                        categoryToBeDeleted.name,
                        categoryToBeDeleted.isSelected
                    )
                    deleteCategory(catergoryEntityToBeDeleted)

                }

            }

        }

        categoryAdapter.setOnClickListener { selected ->
            if (selected.name == "+") {
                val createCategoryBottomSheet = CreateCategoryBottomSheet { categoryName ->
                    val categoryEntity = CategoryEntity(
                        name = categoryName,
                        isSelected = false
                    )

                    insertCategory(categoryEntity)
                }
                createCategoryBottomSheet.show(supportFragmentManager, "createCategoryBottomSheet")
            } else {
                val categoryTemp = categories.map { item ->
                    when {
                        item.name == selected.name && item.isSelected -> item.copy(
                            isSelected = true
                        )

                        item.name == selected.name && !item.isSelected -> item.copy(isSelected = true)
                        item.name != selected.name && item.isSelected -> item.copy(isSelected = false)
                        else -> item
                    }
                }

                if (selected.name != "ALL") {
                    filterTaskByCategoryName(selected.name)
                } else {
                    GlobalScope.launch (Dispatchers.IO){
                        getTaskFromDataBase()
                    }
                }
                categoryAdapter.submitList(categoryTemp)
            }
        }

        rvCategory.adapter = categoryAdapter
        GlobalScope.launch(Dispatchers.IO) {
            getCategoriesFromDataBase()
        }

        //categoryAdapter.submitList(categories)

        rvTask.adapter = taskAdapter


        GlobalScope.launch(Dispatchers.IO) {
            getTaskFromDataBase()
        }
        //taskAdapter.submitList(tasks)
    }

    private fun showInfoDialog(
        title: String,
        description: String,
        btnText: String,
        onClick: () -> Unit
    ) {
        val infoBottomSheet = InfoBottomSheet(
            title = title,
            description = description,
            btnText = btnText,
            onClick
        )
        infoBottomSheet.show(
            supportFragmentManager,
            "InfoBottomSheet"
        )

    }

    private fun getCategoriesFromDataBase() {
        val categoriesFromDb: List<CategoryEntity> = categoryDao.getAll()
        categoriesEntity = categoriesFromDb
        val categoriesUiData = categoriesFromDb.map {
            CategoryUiData(
                name = it.name,
                isSelected = it.isSelected
            )
        }.toMutableList()


        categoriesUiData.add(
            CategoryUiData(
                name = "+",
                isSelected = false
            )
        )

        val categoryListTemp = mutableListOf(
            CategoryUiData(
                name = "ALL",
                isSelected = true,
            )
        )
        categoryListTemp.addAll(categoriesUiData)
        GlobalScope.launch(Dispatchers.Main) {
            categories = categoryListTemp
            categoryAdapter.submitList(categories)
        }

    }

    private fun getTaskFromDataBase() {
        val taskFromDb: List<TaskEntity> = taskDao.getAll()
        val tasksUiData: List<TaskUiData> = taskFromDb.map {
            TaskUiData(
                id = it.id,
                name = it.name,
                category = it.category
            )
        }


        GlobalScope.launch(Dispatchers.Main) {
            tasks = tasksUiData
            taskAdapter.submitList(tasksUiData)
        }
    }

    private fun insertCategory(categoryEntity: CategoryEntity) {
        GlobalScope.launch(Dispatchers.IO) {
            categoryDao.insert(categoryEntity)
            getCategoriesFromDataBase()
        }
    }

    private fun insertTask(taskEntity: TaskEntity) {
        GlobalScope.launch(Dispatchers.IO) {
            taskDao.insert(taskEntity)
            getTaskFromDataBase()
        }
    }

    private fun updateTask(taskEntity: TaskEntity) {
        GlobalScope.launch(Dispatchers.IO) {
            taskDao.update(taskEntity)
            getTaskFromDataBase()
        }
    }

    private fun deleteTask(taskEntity: TaskEntity) {
        GlobalScope.launch(Dispatchers.IO) {
            taskDao.delete(taskEntity)
            getTaskFromDataBase()
        }
    }

    private fun deleteCategory(categoryEntity: CategoryEntity) {
        GlobalScope.launch(Dispatchers.IO) {
            val tasksToBeDelete = taskDao.getAllByCategoryName(categoryEntity.name)
            taskDao.deleteAll(tasksToBeDelete)
            categoryDao.delete(categoryEntity)
            getCategoriesFromDataBase()
            getTaskFromDataBase()
        }
    }

    private fun filterTaskByCategoryName(category: String) {
        GlobalScope.launch(Dispatchers.IO) {
            val tasksFromDb: List<TaskEntity> = taskDao.getAllByCategoryName(category)
            val tasksUiData: List<TaskUiData> = tasksFromDb.map {
                TaskUiData(
                    id = it.id,
                    name = it.name,
                    category = it.category
                )
            }
            GlobalScope.launch(Dispatchers.Main) {
                taskAdapter.submitList(tasksUiData)
            }
        }
    }

        private fun showCreateUpdateTaskBottomSheet(taskUiData: TaskUiData? = null) {
            val createTaskBottomSheet = CreateOrUpdateTaskBottomSheet(
                task = taskUiData,
                categoryList = categoriesEntity,
                onCreateClicked = { taskToBeCreated ->
                    val taskEntityToBeInsert = TaskEntity(
                        name = taskToBeCreated.name,
                        category = taskToBeCreated.category
                    )
                    insertTask(taskEntityToBeInsert)
                },
                onUpdateClicked = { taskToBeUpdated ->
                    val taskEntityToBeUpdate = TaskEntity(
                        id = taskToBeUpdated.id,
                        name = taskToBeUpdated.name,
                        category = taskToBeUpdated.category
                    )
                    updateTask(taskEntityToBeUpdate)
                },
                onDeleteClicked = { taskToBeDeleted ->
                    val taskEntityToBeDelete = TaskEntity(
                        id = taskToBeDeleted.id,
                        name = taskToBeDeleted.name,
                        category = taskToBeDeleted.category
                    )
                    deleteTask(taskEntityToBeDelete)

                }
            )
            createTaskBottomSheet.show(
                supportFragmentManager,
                "createTaskBottomSheet"
            )
        }
    }