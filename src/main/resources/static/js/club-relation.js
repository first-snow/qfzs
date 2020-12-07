$(document).ready(function () {

    // Datatable 实例
    let datatable = null;
    const commonAjaxFailHandler = function (error) {
        return function (res) {
            error(res);
            console.error(res);
        };
    };

    const refreshTable = function (newId) {
        if (datatable === null) {
            datatable = $('#dataTable').DataTable({
                language: {
                    url: '/key3/js/datatable-zh.json'
                },
                dom: "Bfrtip",
                ajax: {
                    url: utils.wrapUrlWithToken('/key3/admin/api/joining/' + newId),
                    dataSrc: function (resp) {
                        utils.hideLoading();
                        return resp.data;
                    },
                },

                order: [4, 'desc'],
                select: 'single',
                responsive: true,
                altEditor: true, // Enable altEditor

                columns: [{
                    data: 'head',
                    render: function (headUrl) {
                        return '<img alt="头像而已" style="width: 3rem; height: 3rem" src="' +
                            headUrl +
                            '" onerror="this.src=\'https://upload.cc/i1/2020/11/30/561A98.png\'"/>'
                    },
                    orderable: false,
                    searchable: false,
                    type: 'readonly',
                }, {
                    data: 'realName'
                }, {
                    data: 'studentNo'
                }, {
                    data: 'position',
                    defaultContent: '-'
                }, {
                    data: 'total',
                    type: 'number'
                }, {
                    data: 'point',
                    type: 'number',
                    defaultContent: '-',
                }, {
                    data: 'absentCounter',
                    type: 'number'
                }, {
                    data: 'admin',
                    defaultContent: '-',
                    type: 'checkbox',
                    render: function (bool) {
                        return bool ? '是' : '否';
                    }
                }],
                buttons: [{
                    text: '<i class="fas fa-plus-square pr-1"></i>添加',
                    name: 'add',
                    className: 'btn btn-success',
                    beforeShow: function (formNode) {
                        // 新建关系时，需要填写学号
                        formNode.find('#studentNo').removeAttr('readonly');
                    }
                }, {
                    extend: 'selected',
                    text: '<i class="fas fa-pen-square pr-1"></i>编辑',
                    name: 'edit',
                    className: 'btn btn-info',
                    beforeShow: function (formNode) {
                        // 修改关系时，不允许改动学号
                        let studentNoInput = formNode.find('#studentNo');
                        studentNoInput.attr('readonly', 'readonly');
                        studentNoInput.tooltip({
                            title: '抱歉，因受到限制，您无法在本页面修改学号，如有需要，请到成员基本信息维护页面进行修改。谢谢！'
                        });
                    }
                }, {
                    extend: 'selected',
                    text: '<i class="fas fa-trash-alt pr-1"></i>删除',
                    name: 'delete',
                    className: 'btn btn-danger'
                }],
                onAddRow: function (datatable, rowdata, success, error) {
                    utils.showLoading('ADDING...');
                    rowdata.clubId = GLOBAL_VAL.nowClubId;
                    utils.ajax("admin/api/joining", rowdata, 'POST', function (res) {
                        success(res.data);
                    }, commonAjaxFailHandler(error), utils.hideLoading);
                },
                onDeleteRow: function (datatable, rowdata, success, error) {
                    let confirmText = prompt('注意，这会同时删除用户加入社团后产生的全部信息全部信息，包括用户的贡献点数、活动室预约时长、职务信息等，如果您仍要删除，请输入该用户的姓名以进行确认', '');
                    if (confirmText !== rowdata.realName) {
                        alert('您取消了操作，用户数据仍然是安全的');
                        return;
                    }
                    utils.ajax('admin/api/joining/' + GLOBAL_VAL.nowClubId + '/' + rowdata.studentNo, {}, 'DELETE', function (res) {
                        utils.showLoading('DELETING...');
                        success(res.data);
                    }, commonAjaxFailHandler(error), utils.hideLoading);
                },
                onEditRow: function (datatable, rowdata, success, error) {
                    utils.showLoading('MODIFYING...');
                    rowdata.clubId = GLOBAL_VAL.nowClubId;
                    utils.ajax("admin/api/joining", rowdata, 'PUT', function (res) {
                        success(res.data);
                    }, commonAjaxFailHandler(error), utils.hideLoading);
                }
            });
        } else if (newId) { // 加载了其他社团
            datatable.ajax.url(utils.wrapUrlWithToken('/key3/admin/api/joining/' + newId)).load();
        } else { // 普通的刷新
            datatable.ajax.reload();
        }
    };

    utils.subscribeEvent(CONST_VAL.clubChangedEventKey, function (club) {
        utils.showLoading();
        refreshTable(club.id);
    });
});